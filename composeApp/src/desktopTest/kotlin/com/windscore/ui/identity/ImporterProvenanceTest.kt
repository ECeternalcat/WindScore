package com.windscore.ui.identity

import com.windscore.core.parser.MusicXmlParser
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class ImporterProvenanceTest {
    @Test fun locatorsCountRestsAndNormalizeDivisionsWithoutRewritingEvents() {
        val xml="""<score-partwise><part-list/><part id="P1"><measure number="7">
            <attributes><divisions>4</divisions></attributes>
            <note><rest/><duration>4</duration></note>
            <note id="original"><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration><voice>upper</voice></note>
            </measure><measure number="7"><attributes><divisions>8</divisions></attributes>
            <note><pitch><step>D</step><octave>4</octave></pitch><duration>8</duration><voice>01</voice></note>
            </measure></part></score-partwise>"""
        val imported=MusicXmlParser.parseXmlWithProvenance(xml.toByteArray())
        val ordinary=MusicXmlParser.parseXml(xml.toByteArray())
        assertEquals(ordinary,imported.score)
        assertEquals(Json.encodeToString(ordinary),Json.encodeToString(imported.score))
        val notes=imported.provenance.notes
        assertEquals(listOf(0,1),notes.map { it.performedId })
        assertEquals(listOf(8L,16L),notes.map { it.tick })
        assertEquals(listOf(8L,8L),notes.map { it.duration })
        assertEquals(listOf("upper","01"),notes.map { it.voice })
        assertEquals(listOf("${ordinary.id}/p0/m0/n1","${ordinary.id}/p0/m1/n0"),notes.map { it.key })
        assertEquals("original",notes.first().xmlId)
        assertFalse(Json.encodeToString(imported.score).contains("performedId"))
    }
}
