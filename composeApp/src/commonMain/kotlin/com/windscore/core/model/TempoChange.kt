package com.windscore.core.model

import kotlinx.serialization.Serializable

/** Quarter notes per minute, effective from a musical tick; never a wall-clock onset. */
@Serializable
data class TempoChange(val tick: Long, val bpm: Double) {
    init {
        require(tick >= 0 && bpm.isFinite() && bpm > 0) { "Tempo changes require a nonnegative tick and positive finite BPM" }
    }
}
