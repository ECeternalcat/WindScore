package com.windscore.ui.identity

import com.windscore.ui.OsmdNoteGeometryJs
import com.windscore.core.loadScore
import com.windscore.core.model.TrackType
import com.windscore.core.parser.MusicXmlParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.*

private val prototypeJson=Json { encodeDefaults=true }

/** Adversarial rendering fixtures now consume authoritative importer provenance. */
class OsmdIdentityPrototypeTest {
    @Serializable
    data class Written(val key: String, val xmlId: String, val part: String, val measure: Int,
        val ordinal: Int, val voice: String, val staff: Int, val tick: Long, val duration: Long,
        val pitch: Int, var performedId: Int = -1, val tieStart: Boolean, val tieStop: Boolean,
        val startNumber: String?, val stopNumber: String?)
    @Serializable
    data class Fixture(val name: String, val xml: String, val divisions: Int, val sourceHash: String, val scoreVersion: Int,
        val notes: List<Written>, val expectedAmbiguous: Int = 0, val expectedUnsupported: Int = 0)

    @Test
    fun sidecarKeepsExistingIdsAcrossTiesAndDuplicateVoices() {
        val f=sidecar("cross-staff",document(crossStaff()))
        assertEquals(listOf(0,0),f.notes.map { it.performedId })
        assertEquals(listOf(1,2),f.notes.map { it.staff })
        val duplicate=sidecar("voices",document(unisons()))
        assertEquals(listOf(0,1),duplicate.notes.map { it.performedId })
        assertEquals(listOf("1","2"),duplicate.notes.map { it.voice })
        val numbered=sidecar("numbered",document(numbered()))
        assertEquals(listOf(0,1,1,0),numbered.notes.map { it.performedId })
    }

    @Test
    fun fullScoreSidecarPreservesEveryProductionIdAndWrittenTieSegment() {
        val f=sidecar("konekuto",loadScore().rawXml!!)
        assertEquals(2049,f.notes.size)
        assertEquals(1708,f.notes.map { it.performedId }.toSet().size)
        assertEquals(341,f.notes.size-f.notes.map { it.performedId }.toSet().size)
        assertEquals(f.notes.size,f.notes.map { it.key }.toSet().size)
        val reference=Json.parseToJsonElement(javaClass.getResource("/konekuto-reference.json")!!.readText()).jsonObject
        val score=loadScore()
        val oracleWritten=reference.getValue("writtenNotes").jsonArray
        assertEquals(oracleWritten.size,f.notes.size)
        for((index,n) in f.notes.withIndex()) {
            val expected=oracleWritten[index].jsonObject
            assertEquals(expected.getValue("pitch").jsonPrimitive.int,n.pitch)
            assertEquals(expected.getValue("startTick").jsonPrimitive.long,n.tick)
            assertEquals(expected.getValue("durationTick").jsonPrimitive.long,n.duration)
            assertEquals(expected.getValue("voice").jsonPrimitive.content,n.voice)
            assertEquals(expected.getValue("staff").jsonPrimitive.int,n.staff)
        }
        // The independent oracle assigns written segments to events without consulting OSMD.
        for(event in reference.getValue("events").jsonArray.map { it.jsonObject }) {
            val actual=score.notes.single {
                it.pitch==event.getValue("pitch").jsonPrimitive.int &&
                it.startTick==event.getValue("startTick").jsonPrimitive.long &&
                it.durationTick==event.getValue("durationTick").jsonPrimitive.long &&
                it.track==(if(event.getValue("staff").jsonPrimitive.int==2)TrackType.LEFT_HAND else TrackType.RIGHT_HAND)
            }
            for(source in event.getValue("sourceNotes").jsonArray)
                assertEquals(actual.id,f.notes[source.jsonPrimitive.int].performedId)
        }
    }

    @Test
    fun actualOsmdMapsSourceNotesAndRebuildsGeometryWithoutPersistingRendererIds() {
        assumeTrue("Opt-in identity research: WINDSCORE_RENDER_VALIDATION=1 and Node/Playwright/Chromium",
            System.getenv("WINDSCORE_RENDER_VALIDATION")=="1")
        val fixtures=listOf(
            sidecar("chord",document(measure(note("C",4)+note("E",4,chord=true)+note("G",4,chord=true)))),
            sidecar("voices",document(unisons())),
            sidecar("divisions",document(measure(note("C",4))+
                measure("<attributes><divisions>8</divisions></attributes>"+note("D",4).replace("<duration>16</duration>","<duration>32</duration>")))),
            sidecar("enharmonic-tie",document(
                measure(note("C",4,tie="start").replace("</step>","</step><alter>1</alter>"))+
                measure(note("D",4,tie="stop").replace("</step>","</step><alter>-1</alter>")))),
            sidecar("missing-voice",document(measure(note("C",4).replace("<voice>1</voice>",""))),unsupported=1),
            sidecar("cross-staff",document(crossStaff())),
            sidecar("cross-staff-chord",document(measure(note("C",4)+note("E",3,staff=2,chord=true)))),
            sidecar("voice-ties",document(measure(note("C",4,tie="start")+"<backup><duration>16</duration></backup>"+note("C",4,voice="2",tie="start"))+
                measure(note("C",4,tie="stop")+"<backup><duration>16</duration></backup>"+note("C",4,voice="2",tie="stop")))),
            sidecar("voice-ties-reversed",document(measure(note("C",4,tie="start")+"<backup><duration>16</duration></backup>"+note("C",4,voice="2",tie="start"))+
                measure(note("C",4,voice="2",tie="stop")+"<backup><duration>16</duration></backup>"+note("C",4,tie="stop")))),
            sidecar("tie-chain",document(measure(note("C",4,tie="start"))+
                measure(note("C",4,tie="stop").replace("<tie type=\"stop\"/>","<tie type=\"stop\"/><tie type=\"start\"/>")
                    .replace("</notations>","<tied type=\"start\"/></notations>"))+
                measure(note("C",4,tie="stop")))),
            sidecar("numbered-unison",document(numbered()),ambiguous=4),
            sidecar("same-voice-unison",document(measure(note("C",4)+note("C",4,chord=true))),ambiguous=2),
            sidecar("string-voices",document(unisons("upper","lower")),unsupported=2),
            sidecar("parts",document(measure(note("C",4)),secondPart=measure(note("C",4)))),
            sidecar("konekuto",loadScore().rawXml!!),
        )
        val root=if(File("tools").isDirectory) File(".") else File("..")
        val output=File(root,"composeApp/build/validation/osmd-identity").apply { mkdirs() }
        File(output,"geometry.js").writeText(OsmdNoteGeometryJs.source)
        File(output,"fixtures.json").writeText(prototypeJson.encodeToString(fixtures))
        File(output,"osmd.js").writeBytes(javaClass.getResourceAsStream("/opensheetmusicdisplay.min.js")!!.readBytes())
        val process=ProcessBuilder("node",File(root,"tools/prototypes/osmd_identity.cjs").absolutePath,output.absolutePath)
            .redirectErrorStream(true).redirectOutput(File(output,"probe.log")).start()
        try {
            assertTrue(process.waitFor(240,TimeUnit.SECONDS),"Identity probe timed out")
            assertEquals(0,process.exitValue(),File(output,"probe.log").readText())
        } finally {if(process.isAlive)process.destroyForcibly()}
    }

    private fun sidecar(name: String, xml: String, ambiguous: Int=0, unsupported: Int=0): Fixture {
        val imported=MusicXmlParser.parseXmlWithProvenance(xml.toByteArray())
        val score=imported.score
        assertEquals(MusicXmlParser.parseXml(xml.toByteArray()),score)
        val written=imported.provenance.notes.map {
            Written(it.key,it.xmlId,it.part,it.measure,it.ordinal,it.voice,it.staff,it.tick,it.duration,
                it.pitch,it.performedId,it.tieStart,it.tieStop,it.startNumber,it.stopNumber)
        }
        return Fixture(name,xml,score.divisions,score.id,score.version,written,ambiguous,unsupported)
    }

    private fun note(step: String,octave: Int,voice: String="1",staff: Int=1,chord: Boolean=false,tie: String?=null,number: Int?=null)=
        """<note id="xml-$step-$octave-$voice-$staff-${tie?:"attack"}-${number?:0}-${if(chord)1 else 0}">${if(chord)"<chord/>" else ""}<pitch><step>$step</step><octave>$octave</octave></pitch><duration>16</duration>${tie?.let { "<tie type=\"$it\"/>" }?:""}<voice>$voice</voice><type>whole</type><staff>$staff</staff>${tie?.let { "<notations><tied type=\"$it\" ${number?.let { n -> "number=\"$n\"" }?:""}/></notations>" }?:""}</note>"""
    private fun measure(notes: String)="<measure number=\"1\">$notes</measure>"
    private fun unisons(a: String="1",b: String="2")=measure(note("C",4,voice=a)+"<backup><duration>16</duration></backup>"+note("C",4,voice=b))
    private fun crossStaff()=measure(note("C",4,tie="start"))+measure(note("C",4,staff=2,tie="stop"))
    private fun numbered()=measure(note("C",4,tie="start",number=1)+note("C",4,chord=true,tie="start",number=2))+
        measure(note("C",4,tie="stop",number=2)+note("C",4,chord=true,tie="stop",number=1))
    private fun document(measures: String,secondPart: String?=null): String {
        val attrs="<attributes><divisions>4</divisions><key><fifths>0</fifths></key><time><beats>4</beats><beat-type>4</beat-type></time><staves>2</staves><clef number=\"1\"><sign>G</sign><line>2</line></clef><clef number=\"2\"><sign>F</sign><line>4</line></clef></attributes>"
        fun part(id: String,m: String)="<part id=\"$id\">"+m.replaceFirst(">",">$attrs")+"</part>"
        val xml="<score-partwise version=\"4.0\"><part-list><score-part id=\"P1\"><part-name>Piano</part-name></score-part>"+
            (if(secondPart!=null)"<score-part id=\"P2\"><part-name>Piano 2</part-name></score-part>" else "")+
            "</part-list>"+part("P1",measures)+(secondPart?.let { part("P2",it) }?:"")+"</score-partwise>"
        var ordinal=0
        return xml.replace("id=\"xml-[^\"]*\"".toRegex()) { "id=\"xml-note-${ordinal++}\"" }
    }
}
