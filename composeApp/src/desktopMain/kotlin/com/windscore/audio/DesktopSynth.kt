package com.windscore.audio

import javax.sound.midi.MidiSystem
import javax.sound.midi.Receiver
import javax.sound.midi.ShortMessage
import javax.sound.midi.Synthesizer

/**
 * Desktop 合成器：复用第一阶段调通的 [MidiSystem.getSynthesizer]（系统默认波表）。
 * 通过 Synthesizer 自身的 Receiver 发送 NoteOn/NoteOff。
 */
class DesktopSynth : AudioSynth {
    private var synth: Synthesizer? = null
    private var receiver: Receiver? = null

    override fun start() {
        if (synth != null) return
        val s = MidiSystem.getSynthesizer()
        s.open()
        synth = s
        receiver = s.receiver
    }

    override fun noteOn(channel: Int, pitch: Int, velocity: Int) {
        val r = receiver ?: return
        val msg = ShortMessage()
        msg.setMessage(ShortMessage.NOTE_ON, channel.coerceIn(0, 15), pitch.coerceIn(0, 127), velocity.coerceIn(0, 127))
        r.send(msg, -1)
    }

    override fun noteOff(channel: Int, pitch: Int) {
        val r = receiver ?: return
        val msg = ShortMessage()
        msg.setMessage(ShortMessage.NOTE_OFF, channel.coerceIn(0, 15), pitch.coerceIn(0, 127), 0)
        r.send(msg, -1)
    }

    override fun stop() {
        try {
            receiver?.close()
        } catch (_: Exception) {
        }
        try {
            synth?.close()
        } catch (_: Exception) {
        }
        receiver = null
        synth = null
    }
}
