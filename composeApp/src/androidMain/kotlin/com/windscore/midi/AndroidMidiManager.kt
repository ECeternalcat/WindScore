package com.windscore.midi

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiOutputPort
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.windscore.core.traceState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AndroidMidiManager(private val context: Context) : MidiManager {
    private val eventQueue = MidiEventQueue()
    override val events: Flow<MidiEvent> = eventQueue.events

    private val _diagnostics = MutableSharedFlow<MidiDiagnostic>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val diagnostics: Flow<MidiDiagnostic> = _diagnostics.asSharedFlow()

    private val sysService = context.getSystemService(Context.MIDI_SERVICE) as? android.media.midi.MidiManager
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val usbFallback = AndroidUsbMidiFallback(
        context,
        emitEvent = { event ->
            traceState("midi.transport source=usb-fallback event=$event")
            eventQueue.emit(event)
        },
        emitDiagnostic = _diagnostics::tryEmit,
    )

    private data class Connection(
        val device: MidiDevice,
        val ports: List<MidiOutputPort>,
    )

    private val lock = Any()
    private val connections = mutableMapOf<Int, Connection>()
    private val pendingDeviceTokens = mutableMapOf<Int, Long>()
    private var nextDeviceToken = 0L
    private var generation = 0

    @Volatile
    private var running = false

    private val deviceCallback = object : android.media.midi.MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) {
            openDevice(info)
        }

        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            closeDevice(info.id)
        }
    }

    override fun start() {
        if (running) return
        running = true
        logMidiEnvironment()
        val mgr = sysService
        if (mgr == null) {
            Log.w(TAG, "系统未提供 MidiManager，启用 USB Host MIDI fallback")
            usbFallback.start()
            return
        }
        synchronized(lock) { generation++ }
        mgr.registerDeviceCallback(deviceCallback, mainHandler)
        Log.i(TAG, "MidiManager 枚举设备数: ${mgr.devices.size}")
        if (mgr.devices.isEmpty()) {
            Log.w(TAG, "MidiManager 未发现 MIDI 设备")
            _diagnostics.tryEmit(MidiDiagnostic.NoInputDevice)
        }
        // 打开所有设备并合并事件流（与 Web MIDI 一致；任一设备失效不影响其余）
        mgr.devices.forEach(::openDevice)
    }

    override fun stop() {
        running = false
        sysService?.unregisterDeviceCallback(deviceCallback)
        usbFallback.stop()
        val toClose = synchronized(lock) {
            generation++
            pendingDeviceTokens.clear()
            connections.values.toList().also { connections.clear() }
        }
        toClose.forEach(::closeConnection)
        eventQueue.clear()
    }

    private fun openDevice(info: MidiDeviceInfo) {
        val mgr = sysService ?: return
        val request = synchronized(lock) {
            if (!running || info.id in connections || info.id in pendingDeviceTokens) return
            val token = ++nextDeviceToken
            pendingDeviceTokens[info.id] = token
            generation to token
        }
        runCatching {
            mgr.openDevice(info, { device -> onDeviceOpened(info.id, request.first, request.second, device) }, mainHandler)
        }.onFailure { error ->
            synchronized(lock) {
                if (pendingDeviceTokens[info.id] == request.second) pendingDeviceTokens.remove(info.id)
            }
            Log.w(TAG, "请求打开设备失败: ${info.id}", error)
            _diagnostics.tryEmit(MidiDiagnostic.DeviceOpenFailed(error))
        }
    }

    private fun onDeviceOpened(deviceId: Int, lifecycleGeneration: Int, deviceToken: Long, device: MidiDevice?) {
        val currentRequest = synchronized(lock) {
            val current = running && generation == lifecycleGeneration && pendingDeviceTokens[deviceId] == deviceToken
            if (current) pendingDeviceTokens.remove(deviceId)
            current
        }
        if (!currentRequest) {
            runCatching { device?.close() }
            return
        }
        if (device == null) {
            Log.w(TAG, "打开设备失败: $deviceId")
            _diagnostics.tryEmit(MidiDiagnostic.DeviceOpenFailed())
            return
        }
        if (!running || synchronized(lock) { lifecycleGeneration != generation }) {
            runCatching { device.close() }
            return
        }

        val ports = device.info.ports.mapNotNull { portInfo ->
            if (portInfo.type != MidiDeviceInfo.PortInfo.TYPE_OUTPUT) return@mapNotNull null
            device.openOutputPort(portInfo.portNumber)?.also { port ->
                val parser = MidiStreamParser { event ->
                    traceState("midi.transport source=android-midi event=$event device=$deviceId")
                    eventQueue.emit(event)
                }
                port.connect(object : android.media.midi.MidiReceiver() {
                    override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
                        parser.feed(data, offset, count)
                    }
                })
            }
        }
        if (ports.isEmpty()) {
            runCatching { device.close() }
            Log.w(TAG, "无可用输出端口: ${device.info.properties}")
            _diagnostics.tryEmit(MidiDiagnostic.DeviceOpenFailed())
            return
        }

        val connection = Connection(device, ports)
        val accepted = synchronized(lock) {
            if (running && lifecycleGeneration == generation && deviceId !in connections) {
                connections[deviceId] = connection
                true
            } else false
        }
        if (accepted) Log.i(TAG, "已连接: ${device.info.properties}") else closeConnection(connection)
    }

    private fun closeDevice(deviceId: Int) {
        val connection = synchronized(lock) {
            pendingDeviceTokens.remove(deviceId)
            connections.remove(deviceId)
        }
        connection?.let(::closeConnection)
        if (connection != null && running) _diagnostics.tryEmit(MidiDiagnostic.Disconnected)
    }

    private fun closeConnection(connection: Connection) {
        connection.ports.forEach { runCatching { it.close() } }
        runCatching { connection.device.close() }
    }

    /** 判断 USB 设备是否含 Audio/MIDI 接口（interface class=1, subclass=3）。 */
    private fun hasMidiInterface(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO && iface.interfaceSubclass == 3) return true
        }
        return false
    }

    /**
     * 全面转储 MIDI/USB 子系统状态，便于定位"连上键盘但 App 无反应"的问题。
     * 用 `adb logcat -s WindScore-MIDI` 查看。
     */
    private fun logMidiEnvironment() {
        Log.i(TAG, "===== MIDI 环境诊断 =====")
        Log.i(TAG, "MidiManager: ${if (sysService != null) "可用" else "不可用(null)"}")
        Log.i(TAG, "UsbManager: ${if (usbManager != null) "可用" else "不可用(null)"}")
        sysService?.devices?.forEach { info ->
            val name = info.properties?.get("name") ?: info.properties?.get("manufacturer")
            Log.i(TAG, "  MidiManager 设备: id=${info.id}, type=${info.type}, name=$name, ports=${info.ports.size}")
        }
        val usb = usbManager
        if (usb == null) {
            Log.i(TAG, "USB 设备列表: UsbManager 不可用")
        } else if (usb.deviceList.isEmpty()) {
            Log.i(TAG, "USB 设备列表: 空（系统未识别到任何 USB 设备）")
        } else {
            usb.deviceList.forEach { (name, device) ->
                Log.i(TAG, "  USB 设备: $name vid=0x${"%04X".format(device.vendorId)} pid=0x${"%04X".format(device.productId)} devClass=${device.deviceClass} hasPermission=${usb.hasPermission(device)} midi=${hasMidiInterface(device)}")
                for (i in 0 until device.interfaceCount) {
                    val iface = device.getInterface(i)
                    Log.i(TAG, "    接口[$i] class=${iface.interfaceClass} subclass=${iface.interfaceSubclass} endpoints=${iface.endpointCount}")
                }
            }
        }
        Log.i(TAG, "===== 诊断结束 =====")
    }

    private companion object {
        const val TAG = "WindScore-MIDI"
    }
}
