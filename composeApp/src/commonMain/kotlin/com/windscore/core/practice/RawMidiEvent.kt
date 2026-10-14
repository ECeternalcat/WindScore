package com.windscore.core.practice

import kotlinx.serialization.Serializable

@Serializable
enum class RawMidiEventType { NOTE_ON, NOTE_OFF }

@Serializable
data class RawMidiEvent(
    val offsetMicros: Long,
    val type: RawMidiEventType,
    val pitch: Int,
    val velocity: Int? = null,
    val source: String? = null,
    val sessionTick: Long? = null,
    val sessionGroup: Int? = null,
)
