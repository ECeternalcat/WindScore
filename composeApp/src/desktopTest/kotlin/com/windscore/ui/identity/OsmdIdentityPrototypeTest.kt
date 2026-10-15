package com.windscore.ui.identity

import com.windscore.core.loadScore
import com.windscore.core.model.TrackType
import com.windscore.core.parser.MusicXmlParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import kotlin.test.*

private val prototypeJson=Json { encodeDefaults=true }

/** Research only: reproduces allocation for a verified sidecar; it is not a second production parser. */
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
    }

    @Test
    fun actualOsmdMapsSourceNotesAndRebuildsGeometryWithoutPersistingRendererIds() {
        assumeTrue("Opt-in identity research: WINDSCORE_RENDER_VALIDATION=1 and Node/Playwright/Chromium",
            System.getenv("WINDSCORE_RENDER_VALIDATION")=="1")
        val fixtures=listOf(
            sidecar("chord",document(measure(note("C",4)+note("E",4,chord=true)+note("G",4,chord=true)))),
            sidecar("voices",document(unisons())),
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
        val score=MusicXmlParser.parseXml(xml.toByteArray()) // authoritative existing IDs, never rewritten
        val factory=DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false)
            setFeature("http://xml.org/sax/features/external-general-entities",false)
            setFeature("http://xml.org/sax/features/external-parameter-entities",false)
        }
        val doc=factory.newDocumentBuilder().parse(xml.byteInputStream())
        fun Element.text(tag: String, fallback: String="")=getElementsByTagName(tag).item(0)?.textContent?.trim()?:fallback
        fun Element.number(type: String): String? {
            val marks=getElementsByTagName("tied")
            return (0 until marks.length).map { marks.item(it) as Element }.firstOrNull { it.getAttribute("type")==type }
                ?.getAttribute("number")?.takeIf { it.isNotBlank() }
        }
        val written=mutableListOf<Written>()
        val parts=doc.getElementsByTagName("part")
        for(pi in 0 until parts.length) {
            val part=parts.item(pi) as Element
            var resolution=4; var tick=0L; var chordTick=0L
            val measures=part.getElementsByTagName("measure")
            for(mi in 0 until measures.length) {
                val children=measures.item(mi).childNodes;var ordinal=0
                for(ci in 0 until children.length) {
                    val el=children.item(ci) as? Element?:continue
                    val duration=el.text("duration","0").toLong()*(score.divisions/resolution)
                    when(el.tagName) {
                        "attributes" -> resolution=el.text("divisions",resolution.toString()).toInt()
                        "backup" -> tick-=duration
                        "forward" -> tick+=duration
                        "note" -> {
                            val index=ordinal++
                            val chord=el.getElementsByTagName("chord").length>0
                            val start=if(chord)chordTick else tick.also { chordTick=it }
                            if(el.getElementsByTagName("pitch").length>0 && el.getElementsByTagName("rest").length==0) {
                                val pitch=(el.text("octave").toInt()+1)*12+
                                    mapOf("C" to 0,"D" to 2,"E" to 4,"F" to 5,"G" to 7,"A" to 9,"B" to 11).getValue(el.text("step"))+
                                    el.text("alter","0").toInt()
                                val marks=el.getElementsByTagName("tie")
                                val ties=(0 until marks.length).map { (marks.item(it) as Element).getAttribute("type") }
                                written+=Written("${score.id}/p$pi/m$mi/n$index",el.getAttribute("id"),part.getAttribute("id"),mi,index,
                                    el.text("voice"),el.text("staff","1").toInt(),start,duration,pitch,
                                    tieStart="start" in ties,tieStop="stop" in ties,startNumber=el.number("start"),stopNumber=el.number("stop"))
                            }
                            if(!chord)tick+=duration
                        }
                    }
                }
            }
        }
        // Prototype-only replay of the documented allocation contract, checked against the real parser below.
        data class Open(val root: Written,var end: Long)
        val opens=mutableMapOf<List<Any?>,MutableList<Open>>()
        val roots=mutableListOf<Open>()
        for(n in written) {
            fun key(number: String?)=listOf(n.part,n.voice,n.pitch,number)
            val stop=opens[key(n.stopNumber)].orEmpty().filter { it.end==n.tick }
            require(!n.tieStop || stop.size<=1)
            val matched=if(n.tieStop)stop.singleOrNull() else null
            val root=matched?.also { opens[key(n.stopNumber)]?.remove(it);it.end=n.tick+n.duration }
                ?: Open(n,n.tick+n.duration).also { n.performedId=roots.size;roots+=it }
            n.performedId=root.root.performedId
            if(n.tieStart)opens.getOrPut(key(n.startNumber)) { mutableListOf() }+=root
        }
        assertEquals(score.notes.size,roots.size)
        for(root in roots) {
            val actual=score.notes.single { it.id==root.root.performedId }
            assertEquals(listOf(root.root.pitch.toLong(),root.root.tick,root.end-root.root.tick),
                listOf(actual.pitch.toLong(),actual.startTick,actual.durationTick))
            assertEquals(if(root.root.staff==2)TrackType.LEFT_HAND else TrackType.RIGHT_HAND,actual.track)
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
