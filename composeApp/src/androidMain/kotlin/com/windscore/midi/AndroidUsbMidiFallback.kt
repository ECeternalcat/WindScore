package com.windscore.midi

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log

/** Raw USB Host fallback for devices whose ROM does not provide android.media.midi.MidiManager. */
internal class AndroidUsbMidiFallback(
    private val context: Context,
    private val emitEvent: (MidiEvent) -> Unit,
    private val emitDiagnostic: (MidiDiagnostic) -> Unit,
) {
    private data class Connection(
        val device: UsbDevice,
        val usbConnection: UsbDeviceConnection,
        val midiInterface: UsbInterface,
        val inputEndpoint: UsbEndpoint,
        val reader: Thread,
    )

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    private val lock = Any()
    private val connections = mutableMapOf<Int, Connection>()
    private val pendingPermissions = mutableSetOf<Int>()
    private var permissionReceiver: BroadcastReceiver? = null
    private var deviceReceiver: BroadcastReceiver? = null

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        val manager = usbManager
        if (manager == null) {
            Log.w(TAG, "USB fallback 不可用: UsbManager=null")
            emitDiagnostic(MidiDiagnostic.Unsupported)
            return
        }

        registerReceivers()
        val midiDevices = manager.deviceList.values.filter(::hasMidiInterface)
        if (midiDevices.isEmpty()) {
            Log.w(TAG, "USB fallback 未发现 USB-MIDI 设备")
            emitDiagnostic(MidiDiagnostic.NoInputDevice)
            return
        }
        midiDevices.forEach(::requestOrOpen)
    }

    fun stop() {
        running = false
        unregisterReceivers()
        val existing = synchronized(lock) {
            pendingPermissions.clear()
            connections.values.toList().also { connections.clear() }
        }
        existing.forEach(::closeConnection)
    }

    private fun requestOrOpen(device: UsbDevice) {
        if (!running) return
        val manager = usbManager ?: return
        if (manager.hasPermission(device)) {
            open(device)
            return
        }

        val shouldRequest = synchronized(lock) { pendingPermissions.add(device.deviceId) }
        if (!shouldRequest) return
        val intent = Intent(USB_PERMISSION_ACTION).setPackage(context.packageName)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            device.deviceId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            },
        )
        Log.i(TAG, "USB fallback 请求权限: ${deviceDescription(device)}")
        runCatching { manager.requestPermission(device, pendingIntent) }
            .onFailure { error ->
                synchronized(lock) { pendingPermissions.remove(device.deviceId) }
                Log.w(TAG, "USB fallback 权限请求失败: ${deviceDescription(device)}", error)
                emitDiagnostic(MidiDiagnostic.DeviceOpenFailed(error))
            }
    }

    private fun open(device: UsbDevice) {
        if (!running || synchronized(lock) { device.deviceId in connections }) return
        val manager = usbManager ?: return
        val endpoint = findInputEndpoint(device)
        if (endpoint == null) {
            Log.w(TAG, "USB fallback 无 MIDI 输入端点: ${deviceDescription(device)}")
            emitDiagnostic(MidiDiagnostic.DeviceOpenFailed())
            return
        }
        val (midiInterface, inputEndpoint) = endpoint
        val usbConnection = runCatching { manager.openDevice(device) }.getOrNull()
        val interfaceReady = usbConnection != null && runCatching {
            usbConnection.claimInterface(midiInterface, true) &&
                (midiInterface.alternateSetting == 0 || usbConnection.setInterface(midiInterface))
        }.getOrDefault(false)
        if (!interfaceReady) {
            runCatching { usbConnection?.close() }
            Log.w(TAG, "USB fallback 无法打开或 claim 接口: ${deviceDescription(device)}")
            emitDiagnostic(MidiDiagnostic.DeviceOpenFailed())
            return
        }

        lateinit var connection: Connection
        val reader = Thread(
            { readLoop(connection) },
            "WindScore-USB-MIDI-${device.deviceId}",
        ).apply { isDaemon = true }
        connection = Connection(device, usbConnection, midiInterface, inputEndpoint, reader)
        val accepted = synchronized(lock) {
            if (running && device.deviceId !in connections) {
                connections[device.deviceId] = connection
                true
            } else {
                false
            }
        }
        if (accepted) {
            Log.i(TAG, "USB fallback 已连接: ${deviceDescription(device)} endpoint=${inputEndpoint.endpointNumber}")
            reader.start()
        } else {
            closeConnection(connection)
        }
    }

    private fun readLoop(connection: Connection) {
        val buffer = ByteArray(connection.inputEndpoint.maxPacketSize.coerceAtLeast(64))
        val decoder = UsbMidiEventDecoder(emitEvent)
        try {
            while (running && synchronized(lock) { connections[connection.device.deviceId] === connection }) {
                val count = connection.usbConnection.bulkTransfer(
                    connection.inputEndpoint,
                    buffer,
                    buffer.size,
                    READ_TIMEOUT_MS,
                )
                val ownsConnection = synchronized(lock) { connections[connection.device.deviceId] === connection }
                if (count > 0 && running && ownsConnection) decoder.feed(buffer, count = count)
            }
        } catch (error: Exception) {
            val ownsConnection = synchronized(lock) { connections[connection.device.deviceId] === connection }
            if (running && ownsConnection) {
                Log.w(TAG, "USB fallback 读取失败: ${deviceDescription(connection.device)}", error)
                emitDiagnostic(MidiDiagnostic.DeviceOpenFailed(error))
            }
        } finally {
            val removed = synchronized(lock) {
                if (connections[connection.device.deviceId] === connection) {
                    connections.remove(connection.device.deviceId)
                    true
                } else {
                    false
                }
            }
            if (removed) closeConnection(connection)
        }
    }

    private fun closeDevice(deviceId: Int, notify: Boolean) {
        val connection = synchronized(lock) { connections.remove(deviceId) } ?: return
        closeConnection(connection)
        if (notify && running) emitDiagnostic(MidiDiagnostic.Disconnected)
    }

    private fun closeConnection(connection: Connection) {
        runCatching { connection.usbConnection.releaseInterface(connection.midiInterface) }
        runCatching { connection.usbConnection.close() }
        connection.reader.interrupt()
        if (Thread.currentThread() !== connection.reader) {
            runCatching { connection.reader.join(READER_JOIN_TIMEOUT_MS) }
        }
    }

    private fun registerReceivers() {
        if (permissionReceiver == null) {
            permissionReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action != USB_PERMISSION_ACTION) return
                    val device = intent.usbDeviceExtra() ?: return
                    synchronized(lock) { pendingPermissions.remove(device.deviceId) }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    Log.i(TAG, "USB fallback 权限结果: granted=$granted ${deviceDescription(device)}")
                    if (granted) open(device) else emitDiagnostic(MidiDiagnostic.DeviceOpenFailed())
                }
            }.also { receiver ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, IntentFilter(USB_PERMISSION_ACTION), Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(receiver, IntentFilter(USB_PERMISSION_ACTION))
                }
            }
        }
        if (deviceReceiver == null) {
            deviceReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val device = intent.usbDeviceExtra() ?: return
                    when (intent.action) {
                        UsbManager.ACTION_USB_DEVICE_ATTACHED -> if (hasMidiInterface(device)) requestOrOpen(device)
                        UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                            synchronized(lock) { pendingPermissions.remove(device.deviceId) }
                            closeDevice(device.deviceId, notify = true)
                        }
                    }
                }
            }.also { receiver ->
                val filter = IntentFilter().apply {
                    addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                    addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(receiver, filter)
                }
            }
        }
    }

    private fun unregisterReceivers() {
        permissionReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        deviceReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        permissionReceiver = null
        deviceReceiver = null
    }

    private fun findInputEndpoint(device: UsbDevice): Pair<UsbInterface, UsbEndpoint>? {
        for (interfaceIndex in 0 until device.interfaceCount) {
            val midiInterface = device.getInterface(interfaceIndex)
            if (!midiInterface.isMidiStreaming()) continue
            for (endpointIndex in 0 until midiInterface.endpointCount) {
                val endpoint = midiInterface.getEndpoint(endpointIndex)
                if (endpoint.direction == UsbConstants.USB_DIR_IN && endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    return midiInterface to endpoint
                }
            }
        }
        return null
    }

    private fun hasMidiInterface(device: UsbDevice): Boolean =
        (0 until device.interfaceCount).any { device.getInterface(it).isMidiStreaming() }

    private fun UsbInterface.isMidiStreaming(): Boolean =
        interfaceClass == UsbConstants.USB_CLASS_AUDIO &&
            interfaceSubclass == USB_SUBCLASS_MIDI_STREAMING &&
            interfaceProtocol == USB_MIDI_1_PROTOCOL

    @Suppress("DEPRECATION")
    private fun Intent.usbDeviceExtra(): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }

    private fun deviceDescription(device: UsbDevice): String =
        "${device.deviceName} vid=0x${device.vendorId.toString(16).padStart(4, '0')} " +
            "pid=0x${device.productId.toString(16).padStart(4, '0')}"

    private companion object {
        const val TAG = "WindScore-MIDI"
        const val USB_PERMISSION_ACTION = "com.windscore.USB_MIDI_FALLBACK_PERMISSION"
        const val USB_SUBCLASS_MIDI_STREAMING = 3
        const val USB_MIDI_1_PROTOCOL = 0
        const val READ_TIMEOUT_MS = 250
        const val READER_JOIN_TIMEOUT_MS = 500L
    }
}
