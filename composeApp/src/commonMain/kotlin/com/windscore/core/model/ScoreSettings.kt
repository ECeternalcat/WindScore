package com.windscore.core.model

import com.windscore.ViewMode
import kotlinx.serialization.Serializable

@Serializable
data class ScoreSettings(
    val practiceMode: PracticeMode = PracticeMode.BOTH,
    val viewMode: ViewMode = ViewMode.SHEET_FULL,
    val speedMultiplier: Double = 1.0,
    val loopStartTick: Long? = null,
    val loopEndTick: Long? = null,
    val metronomeVol: Int = 0,
    val countdownEnabled: Boolean = false,
    val currentTick: Long = 0L,
    val scoreVersion: Int? = null,
    val divisions: Int? = null,
)
