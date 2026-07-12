package com.windscore.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager.OnDeviceOpenedListener
import android.media.midi.MidiOutputPort
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AndroidMidiManager(context: Context) : MidiManager {
    private val _events = MutableSharedFlow<MidiEvent>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: Flow<MidiEvent> = _events.asSharedFlow()

    private val sysService = context.getSystemService(Context.MIDI_SERVICE) as android.media.midi.MidiManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val openedDevices = mutableListOf<MidiDevice>()
    private val outputPorts = mutableListOf<MidiOutputPort>()

    @Volatile
    private var running = false

    private val receiver = object : android.media.midi.MidiReceiver() {
        override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
            var i = offset
            val end = offset + count
            while (i < end) {
                val b = data[i].toInt() and 0xFF
                if (b and 0x80 == 0) {
                    // 数据字节/running status: Step 1 阶段简单跳过
                    i++
                    continue
                }
                val status = b and 0xF0
                when (status) {
                    0x90 -> {
                        if (i + 2 < end) {
                            val note = data[i + 1].toInt() and 0xFF
                            val velocity = data[i + 2].toInt() and 0xFF
                            if (velocity > 0) {
                                _events.tryEmit(MidiEvent.NoteOn(note, velocity))
                            } else {
                                _events.tryEmit(MidiEvent.NoteOff(note))
                            }
                            i += 3
                        } else {
                            i = end
                        }
                    }
                    0x80 -> {
                        if (i + 2 < end) {
                            val note = data[i + 1].toInt() and 0xFF
                            _events.tryEmit(MidiEvent.NoteOff(note))
                            i += 3
                        } else {
                            i = end
                        }
                    }
                    // Program Change / Channel Pressure: 2 字节
                    0xC0, 0xD0 -> i += 2
                    // Poly Aftertouch / CC / Pitch Bend: 3 字节 (CC 即踏板信号，硬过滤 文档 3.3)
                    0xA0, 0xB0, 0xE0 -> i += 3
                    else -> i++
                }
            }
        }
    }

    private val deviceCallback = OnDeviceOpenedListener { device ->
        if (device == null) {
            println("[MIDI] 打开设备失败")
            return@OnDeviceOpenedListener
        }
        val ports = device.info.ports
        var openedAny = false
        for (portInfo in ports) {
            if (portInfo.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT) {
                val port = device.openOutputPort(portInfo.portNumber)
                if (port != null) {
                    port.connect(receiver)
                    synchronized(openedDevices) {
                        outputPorts.add(port)
                    }
                    openedAny = true
                }
            }
        }
        if (openedAny) {
            synchronized(openedDevices) {
                openedDevices.add(device)
            }
            println("[MIDI] 已连接: ${device.info.properties}")
        } else {
            println("[MIDI] 无可用输出端口: ${device.info.properties}")
        }
    }

    override fun start() {
        if (running) return
        running = true
        val devices = sysService.devices
        if (devices.isEmpty()) {
            println("[MIDI] 未发现 MIDI 设备")
            return
        }
        // 打开所有设备并合并事件流（与 Web MIDI 一致；任一设备失效不影响其余）
        for (info in devices) {
            sysService.openDevice(info, deviceCallback, mainHandler)
        }
    }

    override fun stop() {
        running = false
        synchronized(openedDevices) {
            for (port in outputPorts) runCatching { port.close() }
            for (d in openedDevices) runCatching { d.close() }
            outputPorts.clear()
            openedDevices.clear()
        }
    }
}
