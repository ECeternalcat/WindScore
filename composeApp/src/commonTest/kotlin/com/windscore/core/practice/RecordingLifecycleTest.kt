package com.windscore.core.practice

import com.windscore.ViewMode
import com.windscore.audio.AudioSynth
import com.windscore.core.AppDiagnostic
import com.windscore.core.DiagnosticController
import com.windscore.core.MidiInputController
import com.windscore.core.MidiNoteHandler
import com.windscore.core.engine.*
import com.windscore.core.model.*
import com.windscore.midi.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlin.test.*

/** No Default-thread polling of recorder state: synchronous lane + durable queue barriers. */
class RecordingLifecycleTest {
    private class Clock : MonotonicMicrosClock {
        var micros = 0L
        var wall = 100L
        override fun nowMicros() = micros
    }
    private class Store : AttemptDataSource {
        val saved = mutableListOf<PracticeAttempt>()
        val cleanupStarted = CompletableDeferred<Unit>()
        var cleanupGate: CompletableDeferred<Unit>? = null
        var saveGate: CompletableDeferred<Unit>? = null
        var failSave = false
        val savedSignal = CompletableDeferred<Unit>()
        override suspend fun cleanupTempFiles(): Int {
            cleanupStarted.complete(Unit)
            cleanupGate?.await()
            return 0
        }
        override suspend fun saveAttempt(attempt: PracticeAttempt) {
            saveGate?.await()
            if (failSave) error("save failure sentinel")
            saved += attempt
            savedSignal.complete(Unit)
        }
        override suspend fun loadAttempts(scoreId: String) = saved.filter { it.context.scoreId == scoreId }
    }
    private object Synth : AudioSynth {
        override fun start() = Unit
        override fun noteOn(channel: Int, pitch: Int, velocity: Int) = Unit
        override fun noteOff(channel: Int, pitch: Int) = Unit
        override fun stop() = Unit
    }
    private object Manager : MidiManager {
        override val events: Flow<MidiEvent> = emptyFlow()
        override val diagnostics: Flow<MidiDiagnostic> = emptyFlow()
        override fun start() = Unit
        override fun stop() = Unit
    }
    private fun score(vararg pitches: Int, hostedTail: Boolean = false) = Score(
        notes = pitches.mapIndexed { i, pitch -> NoteEntity(i, pitch, TrackType.RIGHT_HAND, i * 4L, 4) } +
            if (hostedTail) listOf(NoteEntity(100, 48, TrackType.LEFT_HAND, pitches.size * 4L, 4),
                NoteEntity(101, 50, TrackType.LEFT_HAND, (pitches.size + 1) * 4L, 4)) else emptyList(),
        divisions = 40, tempo = 120, totalTicks = (pitches.size + if (hostedTail) 2 else 0) * 4L,
        id = "lifecycle-score", version = 1,
    )
    private class Rig(val scope: CoroutineScope, val score: Score, mode: PracticeMode = PracticeMode.BOTH) {
        val clock = Clock()
        val store = Store()
        val session = ScoreSession(score, Synth, scope, mode)
        val recording = PracticeRecordingController(scope, store, clock = clock, wallClockMillis = { clock.wall })
        val input = MidiInputController(Manager, Synth, scope).apply {
            recordingSink = recording
            register(object : MidiNoteHandler {
                override fun onNoteOn(pitch: Int): Boolean { session.onNoteOn(pitch); return true }
                override fun onNoteOff(pitch: Int): Boolean { session.onNoteOff(pitch); return true }
            })
        }
        fun conditions(speed: Float = 1f) = AttemptConditions(score, session.practiceMode, ViewMode.SHEET_FULL,
            session.takeIf { it.isLoopActive }?.loopRange, speed, session)
        fun begin(speed: Float = 1f) { session.setPracticeSpeed(speed); recording.beginPracticeSession(conditions(speed)) }
        fun on(pitch: Int) { input.dispatch(MidiEvent.NoteOn(pitch, 90)) }
        fun off(pitch: Int) { input.dispatch(MidiEvent.NoteOff(pitch)) }
        fun close() { recording.close(); input.close(); session.close() }
    }

    @Test fun lastInputFinalizesImmediatelyAndTrailingReleaseIsNotFabricated() = runBlocking {
        val rig = Rig(this, score(60))
        try {
            rig.begin()
            rig.clock.micros = 500
            rig.on(60)
            assertNull(rig.recording.activeAttemptId)
            rig.off(60)
            rig.recording.awaitPendingWrites()
            val attempt = rig.store.saved.single()
            assertEquals(listOf(RawMidiEventType.NOTE_ON), attempt.events.map { it.type })
            assertEquals(0L, attempt.events.single().sessionTick)
            assertEquals(0, attempt.events.single().sessionGroup)
            assertEquals(500L, attempt.durationMicros)
            assertEquals(0L, attempt.lastCompletedGroupTick)
            assertEquals(4L, attempt.boundaryTick)
            assertEquals(setOf(60), attempt.heldAtBoundaryPitches)
            assertTrue(attempt.releasesMayBeOutsideAttempt)
            rig.recording.onMidiDisconnected()
            rig.recording.onMidiEnabledChanged(true)
            rig.on(60)
            rig.recording.awaitPendingWrites()
            assertEquals(1, rig.store.saved.size)
        } finally { rig.close() }
    }

    @Test fun singleGroupFinalScoreLoopMakesTwoPassesDespiteIdenticalState() = runBlocking {
        val rig = Rig(this, score(60))
        try {
            rig.session.setLoop(0, 4)
            rig.begin()
            val before = rig.session.state.value
            rig.on(60)
            val secondId = rig.recording.activeAttemptId
            rig.off(60)
            rig.on(60)
            assertNotEquals(secondId, rig.recording.activeAttemptId)
            assertEquals(before.currentTick, rig.session.currentTick)
            assertEquals(before.progress, rig.session.progress)
            rig.recording.awaitPendingWrites()
            assertEquals(2, rig.store.saved.size)
            assertTrue(rig.store.saved.all { it.endReason == AttemptEndReason.COMPLETED })
            assertEquals(1, rig.store.saved.map { it.sessionId }.distinct().size)
            assertEquals(setOf(60), rig.store.saved[1].carriedInPitches)
            assertEquals(listOf(RawMidiEventType.NOTE_OFF, RawMidiEventType.NOTE_ON), rig.store.saved[1].events.map { it.type })
            assertEquals(4L, rig.store.saved[1].boundaryTick)
        } finally { rig.close() }
    }

    @Test fun multipleGroupLoopEndingAtScoreEndRepeatsWithoutDuplicateCompletion() = runBlocking {
        val rig = Rig(this, score(60, 62))
        try {
            rig.session.setLoop(0, 8)
            rig.begin()
            repeat(2) { rig.on(60); rig.off(60); rig.on(62); rig.off(62) }
            rig.recording.endSession()
            rig.recording.endSession()
            rig.recording.awaitPendingWrites()
            assertEquals(2, rig.store.saved.count { it.endReason == AttemptEndReason.COMPLETED })
            assertEquals(2, rig.store.saved.count { it.lastCompletedGroupTick == 4L })
            // The final release is a real event in the new partial pass, not a synthetic tail.
            assertEquals(AttemptEndReason.NAVIGATED_AWAY, rig.store.saved.last().endReason)
        } finally { rig.close() }
    }

    @Test fun restoredStartAndSeekStepDoNotClaimCompletionOrTraversal() = runBlocking {
        val rig = Rig(this, score(60, 62, 64))
        try {
            rig.session.seekTo(4)
            rig.begin()
            rig.on(99)
            rig.recording.changePractice { rig.session.stepForward(); rig.conditions() }
            rig.on(64)
            rig.recording.awaitPendingWrites()
            val stopped = rig.store.saved.first()
            val completed = rig.store.saved.last()
            assertEquals(4L, stopped.context.startTick)
            assertEquals(AttemptEndReason.USER_STOPPED, stopped.endReason)
            assertEquals(8L, completed.context.startTick)
            assertEquals(0L, completed.context.regionStartTick)
            assertEquals(8L, completed.reachedTick)
            assertEquals(12L, completed.boundaryTick)
            assertNull(completed.context.observedTempo)
            rig.recording.changePractice { rig.session.seekTo(12); rig.conditions() }
            assertNull(rig.recording.activeAttemptId)
            rig.recording.awaitPendingWrites()
            assertEquals(2, rig.store.saved.size)
        } finally { rig.close() }
    }

    @Test fun speedBoundaryUsesOldSnapshotThenFreshPositionAndClock() = runBlocking {
        val rig = Rig(this, score(60, 62))
        try {
            rig.begin()
            rig.on(60)
            rig.clock.wall = 900
            rig.clock.micros = 1000
            rig.recording.changePractice { rig.session.setPracticeSpeed(0.5f); rig.conditions(0.5f) }
            rig.on(62)
            rig.recording.awaitPendingWrites()
            val first = rig.store.saved[0]
            val second = rig.store.saved[1]
            assertEquals(120, first.context.requestedTempo)
            assertEquals(60, second.context.requestedTempo)
            assertEquals(60, second.context.effectiveHostedTempo)
            assertEquals(0.5f, second.context.speed)
            assertEquals(4L, second.context.startTick)
            assertEquals(900L, second.context.startedAt)
            assertEquals(0L, second.events.first().offsetMicros)
            assertEquals(1000L, first.durationMicros)
        } finally { rig.close() }
    }

    @Test fun zeroSpeedStopsHostedGroupsAndResumesAtPositiveSpeed() = runBlocking {
        val rig = Rig(this, score(60, hostedTail = true), PracticeMode.RIGHT_ONLY)
        try {
            rig.begin(0f)
            rig.on(60)
            assertEquals(1, rig.session.progress, "zero speed must not advance a pure-hosted group")
            delay(75)
            assertEquals(1, rig.session.progress)
            rig.recording.changePractice { rig.session.setPracticeSpeed(1f); rig.conditions(1f) }
            // Explicit user restart/seek commands pause hosting until fresh interaction.
            rig.on(99)
            withTimeout(1500) { rig.session.state.first { it.finished } }
            rig.recording.awaitPendingWrites()
            assertEquals(0f, rig.store.saved.first().context.speed)
            assertNull(rig.store.saved.first().context.requestedTempo)
            assertNull(rig.store.saved.first().context.effectiveHostedTempo)
            assertEquals(AttemptEndReason.COMPLETED, rig.store.saved.last().endReason)
        } finally { rig.close() }
    }

    @Test fun scoringHostedTailFinalizesBeforeResultDelayAtBaseTempo() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val rig = Rig(scope, score(60, hostedTail = true), PracticeMode.RIGHT_ONLY)
        val scoring = ScoringController(Synth, scope, now = { 1 }, onRecord = { _, _ -> }, resultDelayMs = 1000)
        try {
            rig.begin(0.25f)
            val session = scoring.start(rig.score, PracticeMode.RIGHT_ONLY)
            rig.recording.scoringStarted(rig.conditions(0.25f).copy(session = session))
            rig.recording.onMidiEvent(MidiEvent.NoteOn(60, 100))
            scoring.onNoteOn(60)
            withTimeout(1500) { session.state.first { it.finished } }
            rig.recording.awaitPendingWrites()
            assertEquals(ScoringPhase.ACTIVE, scoring.state.value.phase)
            val attempt = rig.store.saved.single()
            assertEquals(AttemptEndReason.COMPLETED, attempt.endReason)
            assertEquals(AttemptType.SCORING, attempt.context.attemptType)
            assertEquals(120, attempt.context.requestedTempo)
            assertEquals(1f, attempt.context.speed)
            assertEquals(8L, attempt.lastCompletedGroupTick)
            assertEquals(1, attempt.events.size, "hosted synth events never enter raw MIDI")
        } finally { scoring.close(); rig.close(); scope.cancel() }
    }

    @Test fun playbackSuspendsBeforePlayerAndCannotFightPracticeJudgment() = runBlocking {
        val rig = Rig(this, score(60, 62))
        val player = PlaybackController(Synth, this)
        try {
            player.load(rig.score, rig.session, speed = 0f)
            rig.begin(0f)
            player.onPlaybackActiveChanged = rig.recording::setPlaybackActive
            rig.on(99)
            player.toggle()
            assertNull(rig.recording.activeAttemptId)
            rig.on(60)
            assertEquals(0, rig.session.progress)
            assertFalse(rig.session.isHostedEnabled)
            player.toggle()
            assertNotNull(rig.recording.activeAttemptId)
            assertTrue(rig.session.isHostedEnabled)
            rig.on(60)
            rig.recording.endSession()
            rig.recording.awaitPendingWrites()
            assertEquals(2, rig.store.saved.size)
            assertEquals(listOf(99), rig.store.saved[0].events.map { it.pitch })
            assertEquals(listOf(60), rig.store.saved[1].events.map { it.pitch })
        } finally { rig.close(); player.close() }
    }

    @Test fun disableDisconnectCloseAndSupersededCallbackCannotResurrect() = runBlocking {
        val rig = Rig(this, score(60, 62))
        try {
            rig.begin()
            rig.on(99)
            val stale = rig.session.onProgress!!
            rig.recording.onMidiEnabledChanged(false)
            assertNull(rig.recording.activeAttemptId)
            rig.recording.onMidiDisconnected()
            rig.recording.onMidiEnabledChanged(true)
            assertNotNull(rig.recording.activeAttemptId)
            rig.on(98)
            rig.recording.endSession()
            rig.recording.onMidiEnabledChanged(true)
            rig.recording.onMidiDisconnected()
            stale(rig.session, SessionProgress(ProgressOrigin.HOSTED, 4, 1, 8, true, false, 0))
            assertNull(rig.recording.activeAttemptId)
            rig.recording.awaitPendingWrites()
            assertEquals(2, rig.store.saved.size)
            rig.recording.beginPracticeSession(rig.conditions())
            assertNull(rig.recording.activeAttemptId, "disconnect requires explicit fresh enable")
            rig.recording.onMidiEnabledChanged(true)
            assertNotNull(rig.recording.activeAttemptId)
            rig.recording.close()
            rig.recording.beginPracticeSession(rig.conditions())
            assertNull(rig.recording.activeAttemptId)
        } finally { rig.close() }
    }

    @Test fun cleanupAndWritesAreOrderedAndSurviveUiScopeCancellation() = runBlocking {
        val ui = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val store = Store().apply { cleanupGate = CompletableDeferred() }
        val recorder = PracticeRecordingController(ui, store, clock = Clock())
        try {
            recorder.beginPracticeSession(AttemptConditions(score(60), PracticeMode.BOTH, ViewMode.ROLL, null, 1f))
            recorder.onMidiEvent(MidiEvent.NoteOn(60, 80))
            recorder.endSession()
            ui.cancel()
            withTimeout(1000) { store.cleanupStarted.await() }
            assertFalse(store.savedSignal.isCompleted)
            store.cleanupGate!!.complete(Unit)
            recorder.awaitPendingWrites()
            assertEquals(1, recorder.loadDurableAttempts("lifecycle-score").size)
        } finally { store.cleanupGate?.complete(Unit); recorder.close(); ui.cancel() }
    }

    @Test fun failedSaveMakesDurableLoadingFailExplicitly() = runBlocking<Unit> {
        val store = Store().apply { failSave = true }
        val recorder = PracticeRecordingController(this, store, clock = Clock())
        try {
            recorder.beginPracticeSession(AttemptConditions(score(60), PracticeMode.BOTH, ViewMode.ROLL, null, 1f))
            recorder.onMidiEvent(MidiEvent.NoteOn(60, 80))
            recorder.endSession()
            assertFailsWith<IllegalStateException> { recorder.awaitPendingWrites() }
            assertFailsWith<IllegalStateException> { recorder.loadDurableAttempts("lifecycle-score") }
        } finally { recorder.close() }
    }

    @Test fun whollyHostedSingleGroupLoopHasMusicalWrapCadenceNotBusySpin() = runBlocking {
        val hosted = Score(
            notes = listOf(NoteEntity(0, 48, TrackType.LEFT_HAND, 0, 4)),
            divisions = 40, tempo = 120, totalTicks = 4, id = "hosted-loop",
        )
        val session = ScoreSession(hosted, Synth, this, PracticeMode.RIGHT_ONLY)
        val origin = kotlin.time.TimeSource.Monotonic.markNow()
        val times = mutableListOf<Long>()
        val done = CompletableDeferred<Unit>()
        try {
            session.setLoop(0, 4)
            session.onProgress = { _, event ->
                assertTrue(event.completedPass)
                times += origin.elapsedNow().inWholeMicroseconds
                if (times.size == 4) { session.close(); done.complete(Unit) }
            }
            session.onNoteOn(99) // arms hosting, but does not advance a pure-hosted group
            withTimeout(2500) { done.await() }
            assertEquals(4, times.size)
            // Region4 ticks at120BPM/divisions40 =50ms. Tolerate clock/scheduler precision.
            assertTrue(times.zipWithNext().all { (a, b) -> b - a >= 40_000 }, "wrap intervals: $times")
        } finally { session.close() }
    }

    @Test fun hostedTailAtScoreEndWrapsByRemainingRegionDistance() = runBlocking {
        val hosted = Score(
            notes = listOf(NoteEntity(0, 48, TrackType.LEFT_HAND, 0, 2), NoteEntity(1, 50, TrackType.LEFT_HAND, 2, 6)),
            divisions = 40, tempo = 120, totalTicks = 8, id = "hosted-tail-loop",
        )
        val session = ScoreSession(hosted, Synth, this, PracticeMode.RIGHT_ONLY)
        val origin = kotlin.time.TimeSource.Monotonic.markNow()
        val events = mutableListOf<Pair<Long, Long>>()
        val done = CompletableDeferred<Unit>()
        try {
            session.setLoop(0, 8)
            session.onProgress = { _, event ->
                events += event.completedGroupTick to origin.elapsedNow().inWholeMicroseconds
                if (events.size == 5) { session.close(); done.complete(Unit) }
            }
            session.onNoteOn(99)
            withTimeout(2500) { done.await() }
            assertEquals(listOf(0L, 2L, 0L, 2L, 0L), events.map { it.first })
            // Final onset2 -> end8 -> onset0 is6 ticks/75ms, not a reset-to-zero delay.
            for (index in listOf(2, 4)) assertTrue(events[index].second - events[index - 1].second >= 60_000, "$events")
        } finally { session.close() }
    }

    @Test fun asynchronousCloseHasBoundedDrainAndIndependentTimeoutDiagnostic() = runBlocking {
        val ui = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val diagnostics = DiagnosticController()
        val store = Store().apply { saveGate = CompletableDeferred() }
        val recorder = PracticeRecordingController(ui, store, diagnostics, clock = Clock(), closeTimeoutMs = 30)
        val diagnostic = async(start = CoroutineStart.UNDISPATCHED) { diagnostics.events.first() }
        recorder.beginPracticeSession(AttemptConditions(score(60), PracticeMode.BOTH, ViewMode.ROLL, null, 1f))
        recorder.onMidiEvent(MidiEvent.NoteOn(60, 80))
        ui.cancel()
        recorder.close() // returns synchronously, never waits on IO/Main
        val failure = withTimeout(1500) { diagnostic.await() }
        assertTrue(failure is AppDiagnostic.Recording)
        assertTrue((failure as AppDiagnostic.Recording).cause is TimeoutCancellationException)
        store.saveGate!!.complete(Unit)
        assertNull(recorder.activeAttemptId)
    }
}
