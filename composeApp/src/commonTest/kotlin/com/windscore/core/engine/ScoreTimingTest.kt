package com.windscore.core.engine

import com.windscore.core.model.Score
import com.windscore.core.model.TempoChange
import kotlin.test.*

class ScoreTimingTest {
    @Test
    fun integrationAndInverseAgreeAtChangesAndFractionalPositions() {
        val timing = ScoreTiming(score(listOf(TempoChange(0, 120.0), TempoChange(4, 60.0), TempoChange(8, 240.0))))
        assertEquals(500.0, timing.millisecondsAt(4.0), 1e-9)
        assertEquals(1500.0, timing.millisecondsAt(8.0), 1e-9)
        assertEquals(1750.0, timing.millisecondsAt(12.0), 1e-9)
        for (tick in listOf(0.0, 2.5, 4.0, 5.5, 8.0, 12.0)) {
            assertEquals(tick, timing.tickAtMilliseconds(timing.millisecondsAt(tick)), 1e-9)
        }
        assertEquals(1250.0, timing.millisecondsBetween(4, 12), 1e-9)
    }

    @Test
    fun legacyFixedTempoAndLateFirstMarkHaveAnInitialTempo() {
        assertEquals(1000.0, ScoreTiming(score(emptyList())).millisecondsAt(8.0), 1e-9)
        assertEquals(1500.0, ScoreTiming(score(listOf(TempoChange(4, 60.0)))).millisecondsAt(8.0), 1e-9)
        assertEquals(1000.0, ScoreTiming(score(emptyList()).copy(tempo = 0)).millisecondsAt(8.0), 1e-9)
    }

    @Test
    fun duplicateOrUnsortedTicksAndInvalidTemposAreRejected() {
        assertFailsWith<IllegalArgumentException> { ScoreTiming(score(listOf(TempoChange(4, 60.0), TempoChange(4, 120.0)))) }
        assertFailsWith<IllegalArgumentException> { ScoreTiming(score(listOf(TempoChange(4, 60.0), TempoChange(0, 120.0)))) }
        for (bpm in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { TempoChange(0, bpm) }
        }
    }

    private fun score(changes: List<TempoChange>) = Score(emptyList(), 4, 120, 16, tempoChanges = changes)
}
