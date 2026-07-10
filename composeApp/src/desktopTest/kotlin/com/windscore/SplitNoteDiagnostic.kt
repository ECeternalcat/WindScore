package com.windscore

import com.windscore.core.loadScore
import com.windscore.core.model.TrackType
import kotlin.test.Test

/** 诊断：找出被拆开的"长音"（同 pitch+手、时间相邻或重叠，疑似未合并的 Tie）。 */
class SplitNoteDiagnostic {

    @Test
    fun findSplitLongNotes() {
        val score = loadScore()
        val byHand = score.notes.groupBy { it.track }
        var overlap = 0   // 重叠：n2.start < n1.end（几乎肯定是未合并的 tie）
        var adjacent = 0  // 相邻：n2.start == n1.end（可能是 tie 或自然衔接）
        val samples = mutableListOf<String>()

        for ((track, notes) in byHand) {
            val sorted = notes.sortedBy { it.startTick }
            for (i in 1 until sorted.size) {
                val a = sorted[i - 1]
                val b = sorted[i]
                if (a.pitch != b.pitch) continue
                val aEnd = a.startTick + a.durationTick
                when {
                    b.startTick < aEnd -> {
                        overlap++
                        if (samples.size < 12) samples += "OVERLAP ${track} pitch=${a.pitch} a[${a.startTick}+${a.durationTick}] b[${b.startTick}+${b.durationTick}]"
                    }
                    b.startTick == aEnd -> {
                        adjacent++
                    }
                }
            }
        }

        println("[诊断] 总音符=${score.notes.size} 重叠(疑似未合并tie)=$overlap 相邻(疑似tie/衔接)=$adjacent")
        samples.forEach { println("  $it") }
        println("[诊断] 右手=${score.notes.count{it.track==TrackType.RIGHT_HAND}} 左手=${score.notes.count{it.track==TrackType.LEFT_HAND}}")
    }
}
