package com.windscore

import com.windscore.core.model.PracticeMode
import com.windscore.core.model.Score
import com.windscore.core.model.ScoreSettings

internal fun sameScoreIdentity(left: Score, right: Score): Boolean =
    if (left.id.isNotBlank() && right.id.isNotBlank()) left.id == right.id
    else left.title == right.title && left.totalTicks == right.totalTicks

internal fun restoreLoopSetupState(settings: ScoreSettings): LoopSetupState =
    if (settings.loopStartTick != null && settings.loopEndTick == null) {
        LoopSetupState.WAITING_FOR_END
    } else {
        LoopSetupState.IDLE
    }

internal fun createScoreSettings(
    score: Score,
    practiceMode: PracticeMode,
    viewMode: ViewMode,
    playbackSpeed: Float,
    metronomeVol: Int,
    countdownEnabled: Boolean,
    pendingLoopStart: Long?,
    currentTick: Long,
    activeLoopRange: LongRange?,
): ScoreSettings = ScoreSettings(
    scoreVersion = score.version,
    divisions = score.divisions,
    practiceMode = practiceMode,
    viewMode = viewMode,
    speedMultiplier = playbackSpeed.toDouble(),
    loopStartTick = activeLoopRange?.first ?: pendingLoopStart,
    loopEndTick = activeLoopRange?.last,
    metronomeVol = metronomeVol,
    countdownEnabled = countdownEnabled,
    currentTick = currentTick.takeIf { it < score.totalTicks } ?: 0L,
)

/** Old variable-divisions bookmarks cannot be repaired by one scale factor. */
internal fun ScoreSettings.restorePositionFor(score: Score): ScoreSettings {
    if ((scoreVersion ?: 1) < 2 && !score.legacyTimingCompatible) {
        return copy(currentTick = 0, loopStartTick = null, loopEndTick = null)
    }
    val oldDivisions = divisions ?: return this
    if (oldDivisions <= 0 || oldDivisions == score.divisions) return this
    fun scale(tick: Long): Long = (tick.toDouble() * score.divisions / oldDivisions).toLong()
    return copy(currentTick = scale(currentTick), loopStartTick = loopStartTick?.let(::scale),
        loopEndTick = loopEndTick?.let(::scale))
}
