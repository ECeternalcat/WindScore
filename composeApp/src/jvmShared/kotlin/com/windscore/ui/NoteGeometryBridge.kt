package com.windscore.ui

import com.windscore.core.calculateMd5
import com.windscore.core.model.Score
import com.windscore.core.parser.MusicXmlParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val geometryJson = Json { encodeDefaults = true }

/** Reparse only on a raster-cache miss. Refuse to attach current importer IDs to historical models. */
internal fun importerProvenanceJson(rawXml: String, score: Score?): String? = runCatching {
    score ?: return null
    val imported = MusicXmlParser.parseXmlWithProvenance(rawXml.toByteArray())
    val actual = imported.score
    if (score.id != actual.id || score.version != actual.version || score.divisions != actual.divisions ||
        score.notes != actual.notes || score.totalTicks != actual.totalTicks ||
        score.tempo != actual.tempo || score.tempoChanges != actual.tempoChanges) return null
    geometryJson.encodeToString(imported.provenance)
}.getOrNull()

internal fun noteIdentityCacheSuffix(score: Score?): String =
    score?.let { calculateMd5(geometryJson.encodeToString(it)) } ?: "no-note-identity"

@Serializable
private data class CachedNoteGeometry(val cacheKey: String, val width: Int, val height: Int, val geometry: NoteGeometrySnapshot)

/** Android evaluateJavascript returns a JSON-encoded string, including escaped quotes/backslashes. */
internal fun decodeJavascriptString(result: String?): String? =
    runCatching { geometryJson.decodeFromString<String>(result ?: return null) }.getOrNull()

internal fun decodeNoteGeometry(text: String?, score: Score?, width: Int, height: Int, expectedGeneration: Int? = null): NoteGeometrySnapshot? = runCatching {
    score ?: return null
    val g = geometryJson.decodeFromString<NoteGeometrySnapshot>(text ?: return null)
    require(expectedGeneration == null || g.generation == expectedGeneration)
    require(g.schema == 1 && g.renderer == "2.2.1-release" && g.generation >= 0 && g.layout.isNotBlank())
    require(g.sourceHash == score.id && g.scoreVersion == score.version && g.divisions == score.divisions)
    require(g.pages.isNotEmpty() && g.pages.first() == GeometryPage(width, height))
    require(g.notes.map { it.writtenKey }.distinct().size == g.notes.size)
    require(g.notes.map { it.performedId }.toSet() == score.notes.map { it.id }.toSet())
    val statuses = setOf("mapped", "missing-source", "unsupported-renderer", "unsupported-voice",
        "unsupported-time", "ambiguous-part", "ambiguous-source", "missing-graphical", "missing-page", "uncaptured-page",
        "ambiguous-head", "invalid-geometry")
    val events = score.notes.associateBy { it.id }
    g.notes.forEach { n ->
        if (n.writtenTick != null || n.writtenDuration != null) {
            val start = requireNotNull(n.writtenTick)
            val duration = requireNotNull(n.writtenDuration)
            val event = events.getValue(n.performedId)
            require(start >= event.startTick && duration >= 0 &&
                start <= event.startTick + event.durationTick &&
                duration <= event.startTick + event.durationTick - start)
        }
        require(n.writtenKey.startsWith("${score.id}/p") && n.status in statuses)
        if (n.status == "mapped") {
            require(n.page == 0) // Current raster pipeline captures the first SVG only.
            val r = requireNotNull(n.rect)
            require(listOf(r.x,r.y,r.width,r.height).all { it.isFinite() })
            require(r.width > 0 && r.height > 0 && r.x >= -1 && r.y >= -1 &&
                r.x+r.width <= width+1 && r.y+r.height <= height+1)
        } else require(n.rect == null)
    }
    g
}.getOrNull()

internal fun readNoteGeometry(cacheDir: File, score: Score?, width: Int, height: Int): NoteGeometrySnapshot? = runCatching {
    val cached = geometryJson.decodeFromString<CachedNoteGeometry>(File(cacheDir,"note-geometry.v1.json").readText())
    require(cached.cacheKey == cacheDir.name && cached.width == width && cached.height == height)
    decodeNoteGeometry(geometryJson.encodeToString(cached.geometry), score, width, height)
}.getOrNull()

internal fun writeNoteGeometry(tempDir: File, cacheKey: String, width: Int, height: Int, geometry: NoteGeometrySnapshot?) {
    if (geometry != null) File(tempDir,"note-geometry.v1.json").writeText(
        geometryJson.encodeToString(CachedNoteGeometry(cacheKey,width,height,geometry)))
}
