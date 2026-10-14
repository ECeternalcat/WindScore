package com.windscore.core.practice

import com.windscore.core.engine.SessionLane
import com.windscore.core.nowEpochMillis
import com.windscore.midi.MidiEvent
import kotlin.time.TimeSource

fun interface MonotonicMicrosClock { fun nowMicros(): Long }
private val systemMonotonicOrigin = TimeSource.Monotonic.markNow()
val SystemMonotonicMicrosClock: MonotonicMicrosClock =
    MonotonicMicrosClock { systemMonotonicOrigin.elapsedNow().inWholeMicroseconds }
private object AttemptIdGenerator {
    private var counter = 0L
    fun next(): String = "${nowEpochMillis().toString(36)}-${(++counter).toString(36)}"
}

class AttemptRecorder(
    private val clock: MonotonicMicrosClock = SystemMonotonicMicrosClock,
    private val wallClockMillis: () -> Long = { nowEpochMillis() },
    private val newAttemptId: () -> String = AttemptIdGenerator::next,
    private val listener: (PracticeAttempt) -> Unit = {},
) {
    private var sessionId = ""
    private var active: ActiveAttempt? = null
    // Physical evidence survives wait-engine seek/reset/loop key-pool clearing.
    private val held = mutableSetOf<Int>()
    val activeAttemptId get() = SessionLane.run { active?.attemptId }

    fun startSession(): String = SessionLane.run {
        finalize(AttemptEndReason.NAVIGATED_AWAY)
        sessionId = newAttemptId()
        sessionId
    }

    fun startAttempt(context: PracticeContext): String = SessionLane.run {
        finalize(AttemptEndReason.USER_STOPPED)
        val id = newAttemptId()
        active = ActiveAttempt(id, sessionId, context, clock.nowMicros(), held.toSet())
        id
    }

    fun finishAttempt(reason: AttemptEndReason, boundaryTick: Long? = null): PracticeAttempt? =
        SessionLane.run { finalize(reason, boundaryTick) }

    fun reached(tick: Long, completedGroupTick: Long? = null) = SessionLane.run {
        active?.let { it.reachedTick = tick; if (completedGroupTick != null) it.lastCompletedTick = completedGroupTick }
    }

    fun onMidiEvent(event: MidiEvent, sessionTick: Long? = null, sessionGroup: Int? = null) = SessionLane.run {
        val pitch = when (event) { is MidiEvent.NoteOn -> event.pitch; is MidiEvent.NoteOff -> event.pitch }
        val wasHeld = pitch in held
        when (event) {
            is MidiEvent.NoteOn -> held.add(event.pitch)
            is MidiEvent.NoteOff -> held.remove(event.pitch)
        }
        val current = active ?: return@run
        val offset = (clock.nowMicros() - current.startMicros).coerceAtLeast(current.events.lastOrNull()?.offsetMicros ?: 0)
        if (event is MidiEvent.NoteOff && !wasHeld) current.unmatchedReleases++
        current.events += RawMidiEvent(
            offsetMicros = offset,
            type = if (event is MidiEvent.NoteOn) RawMidiEventType.NOTE_ON else RawMidiEventType.NOTE_OFF,
            pitch = pitch,
            velocity = (event as? MidiEvent.NoteOn)?.velocity,
            sessionTick = sessionTick,
            sessionGroup = sessionGroup,
        )
        if (sessionTick != null) current.reachedTick = sessionTick
    }

    fun onMidiDisconnected() = SessionLane.run {
        finalize(AttemptEndReason.MIDI_DISCONNECTED)
        held.clear() // connection loss makes subsequent physical state unknown
    }

    private fun finalize(reason: AttemptEndReason, boundaryTick: Long? = null): PracticeAttempt? {
        val current = active ?: return null
        active = null
        val attempt = PracticeAttempt(
            attemptId = current.attemptId,
            sessionId = current.sessionId,
            context = current.context,
            events = current.events.toList(),
            endedAt = wallClockMillis(),
            endReason = reason,
            durationMicros = (clock.nowMicros() - current.startMicros).coerceAtLeast(current.events.lastOrNull()?.offsetMicros ?: 0),
            reachedTick = current.reachedTick,
            lastCompletedGroupTick = current.lastCompletedTick,
            boundaryTick = boundaryTick ?: current.reachedTick,
            carriedInPitches = current.carriedIn,
            heldAtBoundaryPitches = held.toSet(),
            unmatchedReleaseCount = current.unmatchedReleases,
            releasesMayBeOutsideAttempt = held.isNotEmpty() || current.carriedIn.isNotEmpty(),
            recordingSchemaVersion = 2,
        )
        listener(attempt)
        return attempt
    }
}

private class ActiveAttempt(
    val attemptId: String,
    val sessionId: String,
    val context: PracticeContext,
    val startMicros: Long,
    val carriedIn: Set<Int>,
    val events: MutableList<RawMidiEvent> = mutableListOf(),
    var reachedTick: Long? = context.startTick,
    var lastCompletedTick: Long? = null,
    var unmatchedReleases: Int = 0,
)
