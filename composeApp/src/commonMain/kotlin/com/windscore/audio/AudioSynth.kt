package com.windscore.audio

/**
 * 软件合成器（SoftSynth）抽象 —— 文档 4.1。
 *
 * 为本身不发声的低端 MIDI 键盘提供按键反馈音源，并用于“单/双手隔离”模式下托管轨道的自动发声。
 * 通道版本（[noteOn]/[noteOff] 带 channel 参数）用于在独立的 MIDI 通道上发声，
 * 例如节拍器使用打击乐通道（GM channel 10 / 索引 9），避免与乐谱音符互相切断。
 */
interface AudioSynth {
    /** 打开音频引擎。 */
    fun start()

    /** 按下某音高（velocity 0..127），在默认通道 0 上发声。 */
    fun noteOn(pitch: Int, velocity: Int) = noteOn(0, pitch, velocity)

    /** 松开某音高，在默认通道 0 上停止发声。 */
    fun noteOff(pitch: Int) = noteOff(0, pitch)

    /** 按下某音高（channel 0..15，velocity 0..127）。 */
    fun noteOn(channel: Int, pitch: Int, velocity: Int)

    /** 松开某音高（channel 0..15）。 */
    fun noteOff(channel: Int, pitch: Int)

    /** 释放音频引擎资源。 */
    fun stop()
}
