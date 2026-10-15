package com.windscore.ui

import kotlinx.serialization.Serializable

/** Rebuildable, page-local snapshot pixels; never musical or persisted event identity. */
@Serializable
data class NoteGeometrySnapshot(
    val schema: Int,
    val sourceHash: String,
    val scoreVersion: Int,
    val divisions: Int,
    val renderer: String,
    val generation: Int,
    val layout: String,
    val pages: List<GeometryPage>,
    val notes: List<NoteGeometry>,
    val elapsedMs: Double,
)
@Serializable data class GeometryPage(val width: Int, val height: Int)
@Serializable data class NoteGeometry(
    val writtenKey: String,
    val performedId: Int,
    val status: String,
    val page: Int? = null,
    val rect: NoteHeadRect? = null,
)
@Serializable data class NoteHeadRect(val x: Double, val y: Double, val width: Double, val height: Double)
