package com.windscore.ui

import com.windscore.ViewMode
import com.windscore.core.model.Score
import com.windscore.core.model.TrackSelection
import com.windscore.core.model.isActiveTrack

/** Target guidance only: never derived from held pitches, completed IDs or renderer tie graphs. */
internal fun sheetTargetCueIndex(
    score: Score,
    geometry: NoteGeometrySnapshot?,
    selection: TrackSelection,
): Map<Long, List<NoteHeadRect>> {
    if (geometry == null || geometry.sourceHash != score.id || geometry.scoreVersion != score.version ||
        geometry.divisions != score.divisions || geometry.schema != 1) return emptyMap()
    val events = score.notes.associateBy { it.id }
    return geometry.notes.groupBy { it.performedId }.mapNotNull { (id, segments) ->
        val event = events[id] ?: return@mapNotNull null
        if (!selection.isActiveTrack(event.track)) return@mapNotNull null
        // Only the attack segment. Tied continuations must not advertise another key press.
        val attack = segments.filter { it.writtenTick == event.startTick }.singleOrNull()
            ?: return@mapNotNull null
        val duration = attack.writtenDuration ?: return@mapNotNull null
        if (duration < 0 || duration > event.durationTick || attack.status != "mapped" || attack.page != 0)
            return@mapNotNull null
        val rect = attack.rect ?: return@mapNotNull null
        event.startTick to rect
    }.groupBy({ it.first }, { it.second })
}

internal fun sheetTargetCuesAt(
    index: Map<Long, List<NoteHeadRect>>,
    viewMode: ViewMode,
    isPlaying: Boolean,
    tick: Long,
): List<NoteHeadRect> =
    if (viewMode == ViewMode.SHEET_KEYBOARD && !isPlaying) index[tick].orEmpty() else emptyList()
