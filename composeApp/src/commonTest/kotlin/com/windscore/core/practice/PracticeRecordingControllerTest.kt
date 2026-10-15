package com.windscore.core.practice

import com.windscore.ViewMode
import com.windscore.core.AppDiagnostic
import com.windscore.core.DiagnosticController
import com.windscore.core.model.PracticeMode
import com.windscore.core.model.Score
import com.windscore.midi.MidiEvent
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PracticeRecordingControllerTest {
    @Test
    fun tempoMetadataRetainsDecimalFixedTempoButDoesNotClaimOneBpmForAMap() = runBlocking {
        for (changing in listOf(false, true)) {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val storage = FakeDataSource()
            val controller = PracticeRecordingController(scope, dataSource = storage, clock = FakeClock())
            try {
                val changes = if (changing) listOf(
                    com.windscore.core.model.TempoChange(0, 120.0), com.windscore.core.model.TempoChange(100, 60.0),
                ) else listOf(com.windscore.core.model.TempoChange(0, 120.5))
                controller.beginPracticeSession(conditions(speed = 0.5f).copy(
                    score = score().copy(tempo = 121, tempoChanges = changes, version = Score.CURRENT_VERSION),
                ))
                controller.onMidiEvent(MidiEvent.NoteOn(60, 100))
                controller.endSession()
                controller.awaitPendingWrites()
                val context = storage.saved.single().context
                assertEquals(if (changing) null else 60, context.requestedTempo)
                assertEquals(if (changing) null else 60, context.effectiveHostedTempo)
                assertEquals(Score.CURRENT_VERSION, context.scoreVersion)
            } finally { controller.close(); scope.cancel() }
        }
    }


    private class FakeClock : MonotonicMicrosClock {
        var micros = 0L
        override fun nowMicros(): Long = micros
    }

    private class FakeDataSource : AttemptDataSource {
        // persist() writes from Dispatchers.IO while tests poll from the test thread
        val saved: MutableList<PracticeAttempt> = java.util.Collections.synchronizedList(mutableListOf())
        var failSave = false

        override suspend fun saveAttempt(attempt: PracticeAttempt) {
            if (failSave) throw IOException("boom")
            synchronized(saved) { saved += attempt }
        }

        override suspend fun loadAttempts(scoreId: String): List<PracticeAttempt> =
            synchronized(saved) { saved.filter { it.context.scoreId == scoreId } }
                .sortedByDescending { it.context.startedAt }

        override suspend fun cleanupTempFiles(): Int = 0
    }

    private fun score() = Score(
        notes = emptyList(),
        divisions = 1,
        tempo = 120,
        totalTicks = 1_000L,
        id = "score-1",
        version = 1,
    )

    private fun conditions(
        mode: PracticeMode = PracticeMode.BOTH,
        viewMode: ViewMode = ViewMode.SHEET_FULL,
        loop: LongRange? = null,
        speed: Float = 1f,
    ) = AttemptConditions(score = score(), mode = mode, viewMode = viewMode, loopRange = loop, speed = speed)

    private suspend fun eventually(predicate: () -> Boolean) {
        repeat(200) {
            if (predicate()) return
            delay(10)
        }
        assertTrue(predicate(), "condition was not reached")
    }

    @Test
    fun beginSessionStartsPracticeAttemptWithExpectedContext() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val storage = FakeDataSource()
        var wall = 100L
        val controller = PracticeRecordingController(
            scope = scope,
            dataSource = storage,
            clock = FakeClock(),
            wallClockMillis = { wall },
        )
        try {
            controller.beginPracticeSession(
                conditions(mode = PracticeMode.LEFT_ONLY, viewMode = ViewMode.SHEET_KEYBOARD, loop = 100L..300L, speed = 0.5f),
            )
            assertNotNull(controller.activeAttemptId)
            controller.onMidiEvent(MidiEvent.NoteOn(60, 100))
            controller.endSession()
            eventually { storage.saved.size == 1 }
        } finally {
            scope.cancel()
        }

        val saved = storage.saved.single()
        assertEquals("score-1", saved.context.scoreId)
        assertEquals(1, saved.context.scoreVersion)
        assertEquals(100L, saved.context.regionStartTick)
        assertEquals(300L, saved.context.regionEndTick)
        assertEquals(PracticeMode.LEFT_ONLY, saved.context.practiceMode)
        assertEquals(HintMode.KEYBOARD, saved.context.hintMode)
        assertEquals(PracticeModeType.SHEET_WITH_KEYBOARD, saved.context.practiceModeType)
        assertEquals(AttemptType.PRACTICE, saved.context.attemptType)
        assertEquals(60, saved.context.requestedTempo)
        assertEquals(100L, saved.context.startedAt)
        assertEquals(AttemptEndReason.NAVIGATED_AWAY, saved.endReason)
        assertEquals(1, saved.events.size)
    }

    @Test
    fun restartFinalizesWithUserRestartedAndStartsNewAttempt() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val storage = FakeDataSource()
        val controller = PracticeRecordingController(scope = scope, dataSource = storage, clock = FakeClock())
        try {
            controller.beginPracticeSession(conditions())
            val first = controller.activeAttemptId
            controller.onMidiEvent(MidiEvent.NoteOn(60, 100))
            controller.restartPracticeAttempt(conditions())
            assertNotNull(first)
            assertTrue(controller.activeAttemptId != first)
            controller.onMidiEvent(MidiEvent.NoteOn(62, 100))
            controller.endSession()
            eventually { storage.saved.size == 2 }
        } finally {
            scope.cancel()
        }

        val endReasons = storage.saved.map { it.endReason }.toSet()
        assertEquals(setOf(AttemptEndReason.USER_RESTARTED, AttemptEndReason.NAVIGATED_AWAY), endReasons)
        assertTrue(storage.saved.all { it.events.size == 1 })
    }

    @Test
    fun conditionChangeKeepsAttemptContextsSeparate() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val storage = FakeDataSource()
        val controller = PracticeRecordingController(scope = scope, dataSource = storage, clock = FakeClock())
        try {
            controller.beginPracticeSession(conditions(mode = PracticeMode.BOTH))
            controller.onMidiEvent(MidiEvent.NoteOn(60, 100))
            controller.practiceConditionsChanged(conditions(mode = PracticeMode.RIGHT_ONLY))
            controller.onMidiEvent(MidiEvent.NoteOn(62, 100))
            controller.endSession()
            eventually { storage.saved.size == 2 }
        } finally {
            scope.cancel()
        }

        val both = storage.saved.first { it.context.practiceMode == PracticeMode.BOTH }
        val right = storage.saved.first { it.context.practiceMode == PracticeMode.RIGHT_ONLY }
        assertEquals(AttemptEndReason.USER_STOPPED, both.endReason)
        assertEquals(AttemptEndReason.NAVIGATED_AWAY, right.endReason)
    }

    @Test
    fun scoringLifecycleRecordsScoringAttemptOverFullRegion() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val storage = FakeDataSource()
        val controller = PracticeRecordingController(scope = scope, dataSource = storage, clock = FakeClock())
        try {
            controller.beginPracticeSession(conditions(loop = 100L..200L))
            controller.onMidiEvent(MidiEvent.NoteOn(60, 100))
            controller.scoringStarted(conditions(mode = PracticeMode.LEFT_ONLY))
            controller.onMidiEvent(MidiEvent.NoteOn(64, 100))
            controller.scoringCompleted()
            controller.practiceResumed(conditions(loop = 100L..200L))
            controller.onMidiEvent(MidiEvent.NoteOn(65, 100))
            controller.endSession()
            eventually { storage.saved.size == 3 }
        } finally {
            scope.cancel()
        }

        val scoring = storage.saved.first { it.context.attemptType == AttemptType.SCORING }
        assertEquals(0L, scoring.context.regionStartTick)
        assertEquals(1_000L, scoring.context.regionEndTick)
        assertEquals(PracticeMode.LEFT_ONLY, scoring.context.practiceMode)
        assertEquals(AttemptEndReason.COMPLETED, scoring.endReason)
        assertEquals(1, scoring.events.size)

        val practiceAttempts = storage.saved.filter { it.context.attemptType == AttemptType.PRACTICE }
        assertEquals(2, practiceAttempts.size)
        assertEquals(100L, practiceAttempts.first().context.regionStartTick)
        assertEquals(
            setOf(AttemptEndReason.USER_STOPPED, AttemptEndReason.NAVIGATED_AWAY),
            practiceAttempts.map { it.endReason }.toSet(),
        )
    }

    @Test
    fun attemptWithoutEventsIsNotPersisted() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val storage = FakeDataSource()
        val controller = PracticeRecordingController(scope = scope, dataSource = storage, clock = FakeClock())
        try {
            controller.beginPracticeSession(conditions())
            controller.endSession()
        } finally {
            scope.cancel()
        }

        delay(200)
        assertTrue(storage.saved.isEmpty())
    }

    @Test
    fun saveFailureReportsDiagnosticWithoutThrowing() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val storage = FakeDataSource().apply { failSave = true }
        val diagnostics = DiagnosticController()
        val received = mutableListOf<AppDiagnostic>()
        val collector = scope.launch { diagnostics.events.collect { received += it } }
        val controller = PracticeRecordingController(
            scope = scope,
            dataSource = storage,
            diagnostics = diagnostics,
            clock = FakeClock(),
        )
        try {
            controller.beginPracticeSession(conditions())
            controller.onMidiEvent(MidiEvent.NoteOn(60, 100))
            controller.endSession()
        } finally {
            eventually { received.isNotEmpty() }
            collector.cancel()
            scope.cancel()
        }

        assertTrue(received.single() is AppDiagnostic.Recording)
    }

    @Test
    fun loadAttemptsReturnsSavedAttemptsForScore() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val storage = FakeDataSource()
        var wall = 0L
        val controller = PracticeRecordingController(
            scope = scope,
            dataSource = storage,
            clock = FakeClock(),
            wallClockMillis = { wall },
        )
        try {
            controller.beginPracticeSession(conditions())
            controller.onMidiEvent(MidiEvent.NoteOn(60, 100))
            wall = 50L
            controller.endSession()
            eventually { storage.saved.size == 1 }

            var loaded: List<PracticeAttempt>? = null
            controller.loadAttempts("score-1") { loaded = it }
            eventually { loaded != null }
            assertEquals(1, loaded.orEmpty().size)
            assertEquals("score-1", loaded.orEmpty().single().context.scoreId)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun midiEventWithoutSessionIsIgnored() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val storage = FakeDataSource()
        val controller = PracticeRecordingController(scope = scope, dataSource = storage, clock = FakeClock())
        try {
            controller.onMidiEvent(MidiEvent.NoteOn(60, 100))
            controller.onMidiDisconnected()
        } finally {
            scope.cancel()
        }
        assertTrue(storage.saved.isEmpty())
    }
}
