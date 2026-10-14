package com.windscore.core.practice

import com.windscore.core.model.PracticeMode
import com.windscore.midi.MidiEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttemptRecorderTest {

    private class FakeClock(var micros: Long = 0L) : MonotonicMicrosClock {
        override fun nowMicros(): Long = micros
        fun advance(us: Long) {
            micros += us
        }
    }

    private fun context(startedAt: Long = 0L) = PracticeContext(
        scoreId = "s1",
        scoreVersion = 1,
        regionStartTick = 0L,
        regionEndTick = 100L,
        practiceMode = PracticeMode.BOTH,
        hintMode = HintMode.NONE,
        requestedTempo = 120,
        observedTempo = null,
        practiceModeType = PracticeModeType.SHEET_FULL,
        attemptType = AttemptType.PRACTICE,
        midiDeviceId = null,
        startedAt = startedAt,
    )

    @Test
    fun recordsEventsWithMonotonicNonDecreasingOffsets() {
        val clock = FakeClock()
        val finalized = mutableListOf<PracticeAttempt>()
        val recorder = AttemptRecorder(
            clock = clock,
            wallClockMillis = { 42L },
            newAttemptId = { "a1" },
            listener = { finalized += it },
        )

        recorder.startSession()
        recorder.startAttempt(context())
        recorder.onMidiEvent(MidiEvent.NoteOn(60, 100))
        clock.advance(1_500)
        recorder.onMidiEvent(MidiEvent.NoteOff(60))
        clock.advance(500)
        recorder.onMidiEvent(MidiEvent.NoteOn(62, 90))
        recorder.finishAttempt(AttemptEndReason.USER_STOPPED)

        val attempt = finalized.single()
        assertEquals(listOf(0L, 1_500L, 2_000L), attempt.events.map { it.offsetMicros })
        assertEquals(
            listOf(RawMidiEventType.NOTE_ON, RawMidiEventType.NOTE_OFF, RawMidiEventType.NOTE_ON),
            attempt.events.map { it.type },
        )
        assertEquals(60, attempt.events[0].pitch)
        assertEquals(100, attempt.events[0].velocity)
        assertNull(attempt.events[1].velocity)
        assertEquals(42L, attempt.endedAt)
        assertEquals(AttemptEndReason.USER_STOPPED, attempt.endReason)
    }

    @Test
    fun finishAttemptIsIdempotentAndLaterEventsAreDropped() {
        val finalized = mutableListOf<PracticeAttempt>()
        val recorder = AttemptRecorder(
            clock = FakeClock(),
            wallClockMillis = { 0L },
            newAttemptId = { "a1" },
            listener = { finalized += it },
        )

        recorder.startSession()
        recorder.startAttempt(context())
        recorder.onMidiEvent(MidiEvent.NoteOn(60, 100))

        assertNotNull(recorder.finishAttempt(AttemptEndReason.COMPLETED))
        assertNull(recorder.finishAttempt(AttemptEndReason.COMPLETED))
        recorder.onMidiEvent(MidiEvent.NoteOn(61, 100))

        assertEquals(1, finalized.single().events.size)
    }

    @Test
    fun eventsWhenIdleAreIgnoredWithoutCrash() {
        val finalized = mutableListOf<PracticeAttempt>()
        val recorder = AttemptRecorder(
            clock = FakeClock(),
            wallClockMillis = { 0L },
            newAttemptId = { "a1" },
            listener = { finalized += it },
        )

        recorder.onMidiEvent(MidiEvent.NoteOn(60, 100))
        assertTrue(finalized.isEmpty())
        assertNull(recorder.activeAttemptId)
    }

    @Test
    fun startAttemptFinalizesActiveAttemptDefensively() {
        val finalized = mutableListOf<PracticeAttempt>()
        val ids = mutableListOf("session", "a1", "a2")
        val recorder = AttemptRecorder(
            clock = FakeClock(),
            wallClockMillis = { 0L },
            newAttemptId = { ids.removeAt(0) },
            listener = { finalized += it },
        )

        val session = recorder.startSession()
        val first = recorder.startAttempt(context())
        recorder.onMidiEvent(MidiEvent.NoteOn(60, 100))
        val second = recorder.startAttempt(context())

        assertEquals("a1", first)
        assertEquals("a2", second)
        assertEquals("session", session)
        val finished = finalized.single()
        assertEquals(AttemptEndReason.USER_STOPPED, finished.endReason)
        assertEquals(session, finished.sessionId)
        assertEquals(1, finished.events.size)
    }

    @Test
    fun disconnectFinalizesAndNeverResurrectsAttempt() {
        val finalized = mutableListOf<PracticeAttempt>()
        val ids = ArrayDeque(listOf("session", "a1", "a2"))
        val recorder = AttemptRecorder(
            clock = FakeClock(),
            wallClockMillis = { 0L },
            newAttemptId = { ids.removeFirst() },
            listener = { finalized += it },
        )

        recorder.startSession()
        recorder.startAttempt(context())
        recorder.onMidiEvent(MidiEvent.NoteOn(60, 100))
        recorder.onMidiDisconnected()

        val first = finalized.single()
        assertEquals(AttemptEndReason.MIDI_DISCONNECTED, first.endReason)
        assertNull(recorder.activeAttemptId)
        recorder.onMidiEvent(MidiEvent.NoteOn(64, 80))
        recorder.onMidiDisconnected()
        recorder.finishAttempt(AttemptEndReason.USER_STOPPED)
        assertEquals(1, finalized.size)
    }

    @Test
    fun attemptsShareSessionUntilNewSessionStarts() {
        val finalized = mutableListOf<PracticeAttempt>()
        val ids = ArrayDeque(listOf("s1", "a1", "a2", "s2"))
        val recorder = AttemptRecorder(
            clock = FakeClock(),
            wallClockMillis = { 0L },
            newAttemptId = { ids.removeFirst() },
            listener = { finalized += it },
        )

        val session1 = recorder.startSession()
        recorder.startAttempt(context())
        recorder.finishAttempt(AttemptEndReason.USER_RESTARTED)
        recorder.startAttempt(context())
        recorder.finishAttempt(AttemptEndReason.USER_RESTARTED)
        val session2 = recorder.startSession()

        assertTrue(session1 != session2)
        assertEquals(listOf(session1, session1), finalized.map { it.sessionId })
    }

    @Test
    fun finalizedAttemptSnapshotIsImmutable() {
        val clock = FakeClock()
        val finalized = mutableListOf<PracticeAttempt>()
        val recorder = AttemptRecorder(
            clock = clock,
            wallClockMillis = { 0L },
            newAttemptId = { "a1" },
            listener = { finalized += it },
        )

        recorder.startSession()
        recorder.startAttempt(context())
        recorder.onMidiEvent(MidiEvent.NoteOn(60, 100))
        val finished = recorder.finishAttempt(AttemptEndReason.USER_STOPPED)

        recorder.onMidiEvent(MidiEvent.NoteOn(61, 100))
        assertEquals(1, finished?.events?.size)
        assertEquals(1, finalized.single().events.size)
    }
}
