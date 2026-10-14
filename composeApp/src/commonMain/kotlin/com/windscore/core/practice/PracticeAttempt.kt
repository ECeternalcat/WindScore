package com.windscore.core.practice

import kotlinx.serialization.Serializable

@Serializable
enum class AttemptEndReason {
    COMPLETED,
    USER_STOPPED,
    USER_RESTARTED,
    NAVIGATED_AWAY,
    MIDI_DISCONNECTED,
    APP_INTERRUPTED,
    FREE_PLAY_MARKED,
}

@Serializable
data class PracticeAttempt(
    val attemptId: String,
    val sessionId: String,
    val context: PracticeContext,
    val events: List<RawMidiEvent> = emptyList(),
    val endedAt: Long? = null,
    val endReason: AttemptEndReason? = null,
    val analysisVersion: Int? = null,
    val durationMicros: Long? = null,
    val reachedTick: Long? = null,
    val lastCompletedGroupTick: Long? = null,
    val boundaryTick: Long? = null,
    val carriedInPitches: Set<Int> = emptySet(),
    val heldAtBoundaryPitches: Set<Int> = emptySet(),
    val unmatchedReleaseCount: Int = 0,
    // Immediate onset completion excludes later releases; never infer note duration.
    val releasesMayBeOutsideAttempt: Boolean = false,
    val recordingSchemaVersion: Int = 1,
)
