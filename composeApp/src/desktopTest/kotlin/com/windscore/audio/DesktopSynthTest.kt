package com.windscore.audio

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Phase 2 Step 1：验证 Desktop 软音源可发声，并测量主动 noteOn 的 API 调用延迟
 * （真实音频输出延迟需硬件/示波器，此处验证 API 非阻塞、足够快）。
 */
class DesktopSynthTest {

    @Test
    fun noteOnIsFastAndNonBlocking() {
        val synth = DesktopSynth()
        try {
            synth.start()
        } catch (e: Throwable) {
            // 测试环境可能无音频设备（如 CI/headless），优雅跳过
            println("[SKIP] 无可用音频系统: ${e.message}")
            return
        }

        val scale = listOf(60, 62, 64, 65, 67, 69, 71, 72) // C 大调音阶
        // 预热
        repeat(2) {
            synth.noteOn(60, 100)
            Thread.sleep(50)
            synth.noteOff(60)
            Thread.sleep(20)
        }
        val micros = LongArray(scale.size)
        scale.forEachIndexed { i, pitch ->
            val t0 = System.nanoTime()
            synth.noteOn(pitch, 100)
            micros[i] = (System.nanoTime() - t0) / 1000
            Thread.sleep(150) // 让声音响一会儿
            synth.noteOff(pitch)
            Thread.sleep(20)
        }
        val avgUs = micros.toList().map { it.toDouble() }.average()
        val maxUs = micros.max()
        println("noteOn API 延迟: avg=%.1f us, max=%.1f us (共 ${scale.size} 次)".format(avgUs, maxUs.toDouble()))

        assertTrue(maxUs < 50_000L, "noteOn 调用应 < 50ms（非阻塞），实际 ${maxUs / 1000}ms")
        synth.stop()
    }
}
