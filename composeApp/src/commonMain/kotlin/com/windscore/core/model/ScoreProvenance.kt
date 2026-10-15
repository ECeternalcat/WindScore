package com.windscore.core.model

import kotlinx.serialization.Serializable

/** Importer-owned, rebuildable metadata. Never serialized into Score or used to replace event IDs. */
@Serializable
data class ScoreProvenance(
    val sourceHash: String,
    val scoreVersion: Int,
    val divisions: Int,
    val notes: List<WrittenNote>,
    val schema: Int = 1,
)

@Serializable
data class WrittenNote(
    val key: String,
    val xmlId: String,
    val part: String,
    val measure: Int,
    val ordinal: Int,
    val voice: String,
    val staff: Int,
    val tick: Long,
    val duration: Long,
    val pitch: Int,
    val performedId: Int,
    val tieStart: Boolean,
    val tieStop: Boolean,
    val startNumber: String?,
    val stopNumber: String?,
)

data class ImportedScore(val score: Score, val provenance: ScoreProvenance)
