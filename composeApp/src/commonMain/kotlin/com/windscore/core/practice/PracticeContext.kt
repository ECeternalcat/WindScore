package com.windscore.core.practice

import com.windscore.ViewMode
import com.windscore.core.model.PracticeMode
import kotlinx.serialization.Serializable

@Serializable
enum class HintMode { NONE, KEYBOARD, PIANO_ROLL }

@Serializable
enum class PracticeModeType { PIANO_ROLL, SHEET_WITH_KEYBOARD, SHEET_FULL }

@Serializable
enum class AttemptType { PRACTICE, SCORING, FREE_PLAY }

@Serializable
data class PracticeContext(
    val scoreId: String,
    val scoreVersion: Int,
    val regionStartTick: Long,
    val regionEndTick: Long,
    val practiceMode: PracticeMode,
    val hintMode: HintMode,
    val requestedTempo: Int? = null,
    val observedTempo: Int? = null,
    val practiceModeType: PracticeModeType,
    val attemptType: AttemptType,
    val midiDeviceId: String? = null,
    val startedAt: Long,
    val startTick: Long? = null,
    val divisions: Int? = null,
    val speed: Float? = null,
    val effectiveHostedTempo: Int? = null,
)

fun ViewMode.toPracticeModeType(): PracticeModeType = when (this) {
    ViewMode.ROLL -> PracticeModeType.PIANO_ROLL
    ViewMode.SHEET_KEYBOARD -> PracticeModeType.SHEET_WITH_KEYBOARD
    ViewMode.SHEET_FULL -> PracticeModeType.SHEET_FULL
}

fun ViewMode.toHintMode(): HintMode = when (this) {
    ViewMode.ROLL -> HintMode.PIANO_ROLL
    ViewMode.SHEET_KEYBOARD -> HintMode.KEYBOARD
    ViewMode.SHEET_FULL -> HintMode.NONE
}
