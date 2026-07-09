package com.windscore.audio

/**
 * 软件合成器（SoftSynth）抽象 —— 文档 4.1。
 *
 * 为本身不发声的低端 MIDI 键盘提供按键反馈音源，并用于“单/双手隔离”模式下托管轨道的自动发声。
 */
interface AudioSynth {
    /** 打开音频引擎。 */
    fun start()

    /** 按下某音高（velocity 0..127）。 */
    fun noteOn(pitch: Int, velocity: Int)

    /** 松开某音高。 */
    fun noteOff(pitch: Int)

    /** 释放音频引擎资源。 */
    fun stop()
}
