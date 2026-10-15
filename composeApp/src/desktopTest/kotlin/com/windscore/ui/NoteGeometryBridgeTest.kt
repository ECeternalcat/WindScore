package com.windscore.ui

import com.windscore.core.loadScore
import com.windscore.core.model.Score
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.*

class NoteGeometryBridgeTest {
    private val score get() = loadScore()
    @Test fun provenanceRefusesHistoricalOrChangedEventIdentity() {
        val s=score
        assertNotNull(importerProvenanceJson(s.rawXml!!,s))
        assertNull(importerProvenanceJson(s.rawXml!!,s.copy(version=1)))
        assertNull(importerProvenanceJson(s.rawXml!!,s.copy(notes=s.notes.map { it.copy(id=it.id+1) })))
        assertNull(importerProvenanceJson(s.rawXml!!,null))
        assertNotEquals(noteIdentityCacheSuffix(s),noteIdentityCacheSuffix(s.copy(version=1)))
    }

    private fun sample(s: Score)=NoteGeometrySnapshot(1,s.id,s.version,s.divisions,"2.2.1-release",2,
        "layout-generation-2",listOf(GeometryPage(100,200)),s.notes.mapIndexed { i,n ->
            NoteGeometry("${s.id}/p0/m0/n$i",n.id,"mapped",0,NoteHeadRect(1.0,2.0,3.0,4.0))
        },1.0)
    private fun decode(g: NoteGeometrySnapshot,s: Score=score)=decodeNoteGeometry(Json.encodeToString(g),s,100,200)

    @Test fun staleRendererSourceLayoutAndInvalidRectsAreRejected() {
        val s=score;val g=sample(s)
        assertNotNull(decode(g,s))
        assertNotNull(decodeNoteGeometry(Json.encodeToString(g),s,100,200,2))
        assertNull(decodeNoteGeometry(Json.encodeToString(g),s,100,200,3))
        assertNull(decode(g.copy(schema=2),s))
        assertNull(decode(g.copy(renderer="other"),s))
        assertNull(decode(g.copy(sourceHash="other"),s))
        assertNull(decode(g.copy(scoreVersion=1),s))
        assertNull(decode(g.copy(pages=listOf(GeometryPage(101,200))),s))
        assertNull(decode(g.copy(layout=""),s))
        assertNull(decode(g.copy(notes=g.notes.drop(1)),s))
        assertNull(decode(g.copy(notes=g.notes+g.notes.first()),s))
        assertNull(decode(g.copy(notes=g.notes.map { it.copy(page=1) }),s))
        assertNull(decode(g.copy(notes=g.notes.map { it.copy(rect=NoteHeadRect(999.0,2.0,3.0,4.0)) }),s))
        assertNotNull(decode(g.copy(notes=g.notes.map { it.copy(status="ambiguous-source",rect=null) }),s))
        val events=s.notes.associateBy { it.id }
        val timed=g.copy(notes=g.notes.map {
            val event=events.getValue(it.performedId)
            it.copy(writtenTick=event.startTick,writtenDuration=event.durationTick)
        })
        assertNotNull(decode(timed,s))
        assertNull(decode(timed.copy(notes=timed.notes.map { it.copy(writtenDuration=null) }),s))
        assertNull(decode(timed.copy(notes=timed.notes.map { it.copy(writtenTick=Long.MAX_VALUE) }),s))
        assertNull(decodeNoteGeometry("broken",s,100,200))
    }

    @Test fun androidJavascriptBridgeDecodesNestedJsonAndEscapedLabels() {
        val text=Json.encodeToString(sample(score).copy(layout="layout \"quoted\" \u2603"))
        assertEquals(text,decodeJavascriptString(Json.encodeToString(text)))
        assertNotNull(decodeNoteGeometry(decodeJavascriptString(Json.encodeToString(text)),score,100,200))
        assertNull(decodeJavascriptString("null"))
        assertNull(decodeJavascriptString("broken"))
    }

    @Test fun geometryCacheIsOptionalAndBoundToRasterCacheIdentity() {
        val root=Files.createTempDirectory("windscore-geometry").toFile()
        try {
            val dir=root.resolve("renderer-layout-key").apply { mkdirs() }
            val s=score;val g=sample(s)
            assertNull(readNoteGeometry(dir,s,100,200))
            writeNoteGeometry(dir,dir.name,100,200,g)
            assertEquals(g,readNoteGeometry(dir,s,100,200))
            assertNull(readNoteGeometry(dir,s,101,200))
            val renamed=root.resolve("other-renderer-key")
            assertTrue(dir.renameTo(renamed))
            assertNull(readNoteGeometry(renamed,s,100,200))
            renamed.resolve("note-geometry.v1.json").writeText("{}")
            assertNull(readNoteGeometry(renamed,s,100,200))
        } finally {root.deleteRecursively()}
    }
}
