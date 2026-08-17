package com.windscore.core.engine

import com.windscore.audio.AudioSynth
import com.windscore.core.model.NoteEntity
import com.windscore.core.model.PracticeMode
import com.windscore.core.model.Score
import com.windscore.core.model.ScoreRecord
import com.windscore.core.model.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoringControllerTest {
    @Test
    fun scoringCompletionCreatesOneRecordAndShowsResult() = runBlocking {
        val records = mutableListOf<Pair<String, ScoreRecord>>()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val controller = ScoringController(
                synth = RecordingSynth(),
                scope = scope,
                now = { 123L },
                onRecord = { id, record -> records += id to record },
                resultDelayMs = 0,
            )
            controller.start(score(), PracticeMode.BOTH)

            assertTrue(controller.onNoteOn(60))
            eventually { controller.state.value.phase == ScoringPhase.RESULT }

            assertEquals(1, records.size)
            assertEquals("score", records.single().first)
            assertEquals(100, records.single().second.percent)
            assertEquals(123L, records.single().second.timestamp)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun exitCancelsPendingResultAndReleasesSession() = runBlocking {
        val records = mutableListOf<ScoreRecord>()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val controller = ScoringController(
                synth = RecordingSynth(),
                scope = scope,
                now = { 1L },
                onRecord = { _, record -> records += record },
                resultDelayMs = 100,
            )
            controller.start(score(), PracticeMode.BOTH)
            controller.onNoteOn(60)
            controller.exit()
            delay(150)

            assertEquals(ScoringPhase.SETUP, controller.state.value.phase)
            assertNull(controller.state.value.session)
            assertTrue(records.isEmpty())
            assertFalse(controller.onNoteOn(60))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun restartingScoringReplacesPreviousSession() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val controller = ScoringController(RecordingSynth(), scope, { 1L }, { _, _ -> })
            val first = controller.start(score(), PracticeMode.BOTH)
            val second = controller.start(score(), PracticeMode.RIGHT_ONLY)

            assertTrue(first !== second)
            assertEquals(PracticeMode.RIGHT_ONLY, controller.state.value.session?.practiceMode)
            assertEquals(ScoringPhase.ACTIVE, controller.state.value.phase)
        } finally {
            scope.cancel()
        }
    }

    private suspend fun eventually(predicate: () -> Boolean) {
        repeat(100) {
            if (predicate()) return
            delay(10)
        }
        assertTrue(predicate(), "condition was not reached")
    }

    private fun score() = Score(
        id = "score",
        notes = listOf(NoteEntity(0, 60, TrackType.RIGHT_HAND, 0, 4)),
        divisions = 4,
        tempo = 120,
        totalTicks = 4,
    )

    private class RecordingSynth : AudioSynth {
        override fun start() = Unit
        override fun noteOn(channel: Int, pitch: Int, velocity: Int) = Unit
        override fun noteOff(channel: Int, pitch: Int) = Unit
        override fun stop() = Unit
    }
}
