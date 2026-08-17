package com.windscore.midi

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.sound.midi.MidiDevice
import javax.sound.midi.MidiMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.Receiver
import javax.sound.midi.Sequencer
import javax.sound.midi.ShortMessage
import javax.sound.midi.Synthesizer

class DesktopMidiManager : MidiManager {
    private val eventQueue = MidiEventQueue()
    override val events: Flow<MidiEvent> = eventQueue.events

    private val _diagnostics = MutableSharedFlow<MidiDiagnostic>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val diagnostics: Flow<MidiDiagnostic> = _diagnostics.asSharedFlow()

    private val openedDevices = mutableListOf<MidiDevice>()
    private val openedTransmitters = mutableListOf<javax.sound.midi.Transmitter>()

    @Volatile
    private var running = false

    // 所有设备共用一个 Receiver，合并到同一事件流
    private val sharedReceiver = object : Receiver {
        override fun send(message: MidiMessage, timeStamp: Long) {
            if (message is ShortMessage) handleShort(message)
        }

        override fun close() = Unit
    }

    override fun start() {
        if (running) return
        running = true

        var connected = 0
        for (info in MidiSystem.getMidiDeviceInfo()) {
            val device = try {
                MidiSystem.getMidiDevice(info)
            } catch (e: Exception) {
                // 例如 Windows Code 10（设备无法启动）的虚设备：连句柄都拿不到，静默跳过
                println("[MIDI] 跳过无法访问的设备 ${info.name}: ${e.message}")
                _diagnostics.tryEmit(MidiDiagnostic.DeviceOpenFailed(e))
                continue
            }

            // 只要有 transmitter（能作为 MIDI 输入）；排除 Java 自带的序列器/合成器
            if (device is Sequencer || device is Synthesizer) continue
            if (device.maxTransmitters == 0) continue

            try {
                device.open()
                val transmitter = device.transmitter
                transmitter.receiver = sharedReceiver
                openedDevices.add(device)
                openedTransmitters.add(transmitter)
                connected++
                println("[MIDI] 已连接: ${info.name}")
            } catch (e: Exception) {
                // 例如 Windows 设备管理器报 Code 10（设备无法启动）的虚设备：静默跳过，
                // 继续尝试其余设备（与 Web MIDI 网站一致——监听所有可用输入）。
                println("[MIDI] 跳过无法打开的设备 ${info.name}: ${e.message}")
                _diagnostics.tryEmit(MidiDiagnostic.DeviceOpenFailed(e))
            }
        }

        if (connected == 0) {
            println("[MIDI] 未发现可用的 MIDI 输入设备")
            _diagnostics.tryEmit(MidiDiagnostic.NoInputDevice)
        }
    }

    private fun handleShort(message: ShortMessage) {
        when (message.command) {
            ShortMessage.NOTE_ON -> {
                val velocity = message.data2
                if (velocity > 0) {
                    eventQueue.emit(MidiEvent.NoteOn(message.data1, velocity))
                } else {
                    eventQueue.emit(MidiEvent.NoteOff(message.data1))
                }
            }
            ShortMessage.NOTE_OFF -> {
                eventQueue.emit(MidiEvent.NoteOff(message.data1))
            }
            // CC / 控制信号一律忽略 (文档 3.3: 硬过滤延音踏板等 CC 信号)
            else -> Unit
        }
    }

    override fun stop() {
        running = false
        for (tx in openedTransmitters) runCatching { tx.close() }
        for (d in openedDevices) runCatching { d.close() }
        openedTransmitters.clear()
        openedDevices.clear()
        eventQueue.clear()
    }
}
