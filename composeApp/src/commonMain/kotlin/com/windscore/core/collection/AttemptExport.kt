package com.windscore.core.collection

import com.windscore.core.practice.PracticeAttempt
import com.windscore.core.practice.RawMidiEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Public, allowlisted collection format. Never serialize PracticeAttempt directly. */
@Serializable
data class AttemptExportEnvelope(
    val format: String = "windscore-practice-collection",
    val schemaVersion: Int = 1,
    val appVersion: String,
    val sourceRecordingSchemas: List<Int>,
    val containsRawMidi: Boolean,
    val attempts: List<ExportAttempt>,
)

@Serializable
data class ExportAttempt(
    val scoreAlias: String,
    val scoreVersion: Int,
    val divisions: Int?,
    val attemptAlias: String,
    val sessionAlias: String,
    val attemptType: String,
    val endReason: String?,
    val durationMicros: Long?,
    val regionLengthTicks: Long,
    val startOffsetTicks: Long?,
    val reachedOffsetTicks: Long?,
    val completedGroupOffsetTicks: Long?,
    val boundaryOffsetTicks: Long?,
    val practiceMode: String,
    val hintMode: String,
    val practiceModeType: String,
    val speed: Float?,
    val requestedTempo: Int?,
    val effectiveHostedTempo: Int?,
    val eventCount: Int,
    val noteOnCount: Int,
    val noteOffCount: Int,
    val dataQuality: List<String>,
    val events: List<ExportEvent>? = null,
)

@Serializable
data class ExportEvent(
    val offsetMicros: Long,
    val type: String,
    val pitch: Int,
    val velocity: Int?,
    val sessionOffsetTicks: Long?,
    val sessionGroup: Int?,
)

object AttemptExport {
    val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false }

    /** Alias ordinals are deterministic for a fixed selection, independent of input/click order. */
    fun build(selected: List<PracticeAttempt>, includeRawMidi: Boolean, appVersion: String): AttemptExportEnvelope {
        val ordered = selected.distinctBy { it.context.scoreId to it.attemptId }
            .sortedWith(compareBy<PracticeAttempt>({ it.context.scoreId }, { it.sessionId }, { it.attemptId }))
        val scoreKeys = ordered.map { it.context.scoreId }.distinct().sorted()
        val scoreAliases = scoreKeys.withIndex().associate { (i, scoreId) -> scoreId to "score-${i + 1}" }
        val sessionKeys = ordered.map { it.context.scoreId to it.sessionId }.distinct()
            .sortedWith(compareBy({ it.first }, { it.second }))
        val sessionAliases = sessionKeys.withIndex().associate { (i, key) -> key to "session-${i + 1}" }
        val attempts = ordered.mapIndexed { index, attempt ->
            val c = attempt.context
            val raw = attempt.events
            ExportAttempt(
                scoreAlias = scoreAliases.getValue(c.scoreId),
                scoreVersion = c.scoreVersion,
                divisions = c.divisions,
                attemptAlias = "attempt-${index + 1}",
                sessionAlias = sessionAliases.getValue(c.scoreId to attempt.sessionId),
                attemptType = c.attemptType.name,
                endReason = attempt.endReason?.name,
                durationMicros = attempt.durationMicros,
                regionLengthTicks = (c.regionEndTick - c.regionStartTick).coerceAtLeast(0),
                startOffsetTicks = c.startTick?.minus(c.regionStartTick),
                reachedOffsetTicks = attempt.reachedTick?.minus(c.regionStartTick),
                completedGroupOffsetTicks = attempt.lastCompletedGroupTick?.minus(c.regionStartTick),
                boundaryOffsetTicks = attempt.boundaryTick?.minus(c.regionStartTick),
                practiceMode = c.practiceMode.name,
                hintMode = c.hintMode.name,
                practiceModeType = c.practiceModeType.name,
                speed = c.speed,
                requestedTempo = c.requestedTempo,
                effectiveHostedTempo = c.effectiveHostedTempo,
                eventCount = raw.size,
                noteOnCount = raw.count { it.type.name == "NOTE_ON" },
                noteOffCount = raw.count { it.type.name == "NOTE_OFF" },
                dataQuality = qualityFlags(attempt),
                events = if (includeRawMidi) raw.map { it.toExportEvent(c.regionStartTick) } else null,
            )
        }
        return AttemptExportEnvelope(
            appVersion = appVersion,
            sourceRecordingSchemas = ordered.map { it.recordingSchemaVersion }.distinct().sorted(),
            containsRawMidi = includeRawMidi,
            attempts = attempts,
        )
    }

    fun serialize(selected: List<PracticeAttempt>, includeRawMidi: Boolean, appVersion: String): String =
        json.encodeToString(build(selected, includeRawMidi, appVersion))

    fun suggestedFilename(): String = "windscore-practice-collection.json"

    private fun qualityFlags(attempt: PracticeAttempt): List<String> = buildList {
        if (attempt.releasesMayBeOutsideAttempt) add("boundary_may_exclude_later_releases")
        if (attempt.carriedInPitches.isNotEmpty()) add("keys_were_held_at_attempt_start")
        if (attempt.heldAtBoundaryPitches.isNotEmpty()) add("keys_were_held_at_attempt_end")
        if (attempt.unmatchedReleaseCount > 0) add("unmatched_release_events_observed")
        if (attempt.context.startTick != null && attempt.context.startTick > attempt.context.regionStartTick)
            add("attempt_started_inside_selected_region")
        if (attempt.durationMicros == null) add("duration_unknown")
        if (attempt.events.isEmpty()) add("no_midi_events_recorded")
    }

    private fun RawMidiEvent.toExportEvent(regionStartTick: Long) = ExportEvent(
        offsetMicros = offsetMicros,
        type = type.name,
        pitch = pitch,
        velocity = velocity,
        sessionOffsetTicks = sessionTick?.minus(regionStartTick),
        sessionGroup = sessionGroup,
    )
}
