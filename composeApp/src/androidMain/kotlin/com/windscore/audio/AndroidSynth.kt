package com.windscore.audio

import org.billthefarmer.mididriver.MidiDriver

/**
 * Android 合成器：billthefarmer/mididriver，通过 JNI 调用 Android 底层 Sonivox EAS 引擎。
 * APK 仅增数十 KB、延迟极低（文档 4.1）。MidiDriver 为单例：使用 [MidiDriver.getInstance]。
 */
class AndroidSynth : AudioSynth {
    private var driver: MidiDriver? = null

    override fun start() {
        if (driver != null) return
        driver = MidiDriver.getInstance { /* onMidiStart: 引擎就绪，无需额外初始化 */ }
        driver?.start()
    }

    override fun noteOn(pitch: Int, velocity: Int) {
        driver?.write(
            byteArrayOf(0x90.toByte(), pitch.coerceIn(0, 127).toByte(), velocity.coerceIn(0, 127).toByte())
        )
    }

    override fun noteOff(pitch: Int) {
        driver?.write(byteArrayOf(0x80.toByte(), pitch.coerceIn(0, 127).toByte(), 0))
    }

    override fun stop() {
        try {
            driver?.stop()
        } catch (_: Exception) {
        }
        driver = null
    }
}
