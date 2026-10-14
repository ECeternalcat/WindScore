package com.windscore.core.engine

import com.windscore.core.model.Score
import com.windscore.core.model.TempoChange

/** Piecewise tempo integration and its inverse, independent of transport speed and wait-mode position. */
class ScoreTiming(score: Score) {
    private val changes = buildList {
        val source = score.tempoChanges
        if (source.isEmpty() || source.first().tick > 0) {
            add(TempoChange(0, (score.tempo.takeIf { it > 0 } ?: 120).toDouble()))
        }
        addAll(source)
    }
    private val milliseconds = DoubleArray(changes.size)
    private val msPerTick = changes.map { 60_000.0 / (it.bpm * (score.divisions.takeIf { d -> d > 0 } ?: 4)) }

    init {
        for (i in 1 until changes.size) {
            require(changes[i].tick > changes[i - 1].tick) { "Tempo map must have strictly increasing ticks" }
            milliseconds[i] = milliseconds[i - 1] +
                (changes[i].tick - changes[i - 1].tick) * msPerTick[i - 1]
        }
    }

    fun millisecondsAt(tick: Double): Double {
        val position = tick.coerceAtLeast(0.0)
        val index = segment { changes[it].tick.toDouble() <= position }
        return milliseconds[index] + (position - changes[index].tick) * msPerTick[index]
    }

    fun tickAtMilliseconds(time: Double): Double {
        val position = time.coerceAtLeast(0.0)
        val index = segment { milliseconds[it] <= position }
        return changes[index].tick + (position - milliseconds[index]) / msPerTick[index]
    }

    fun millisecondsBetween(start: Long, end: Long): Double =
        millisecondsAt(end.toDouble()) - millisecondsAt(start.toDouble())

    private inline fun segment(before: (Int) -> Boolean): Int {
        var lo = 0
        var hi = changes.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (before(mid)) lo = mid else hi = mid - 1
        }
        return lo
    }
}
