package com.windscore.core.semantics

import com.windscore.core.parser.MusicXmlParser
import kotlin.test.*

class TieSemanticsTest {
    @Test
    fun sameVoiceAndPitchInDifferentPartsCannotShareATie() {
        val score = parse("<part><measure>${note("start")}</measure></part><part><measure>${note("stop")}</measure></part>")
        assertEquals(listOf(0L to 4L, 0L to 4L), score.notes.map { it.startTick to it.durationTick })
    }

    @Test
    fun startStopStartChainCanCrossStaffAndDivisions() {
        val score = parse("""<part><measure><attributes><divisions>4</divisions></attributes>${note("start")}</measure>
          <measure><attributes><divisions>8</divisions></attributes>${note("stop", staff = 2, duration = 8, extraTie = "start")}</measure>
          <measure>${note("stop", duration = 8)}</measure></part>""")
        assertEquals(listOf(0L to 24L), score.notes.map { it.startTick to it.durationTick })
    }

    @Test
    fun gapAndVoiceChangeDoNotSilentlyMergeIndependentAttacks() {
        for (middle in listOf("<note><rest/><duration>4</duration></note>", "")) {
            val stopVoice = if (middle.isEmpty()) 2 else 1
            val score = parse("<part><measure>${note("start")}$middle${note("stop", voice = stopVoice)}</measure></part>")
            assertEquals(2, score.notes.size)
            assertEquals(listOf(4L, 4L), score.notes.map { it.durationTick })
        }
    }

    @Test
    fun numberedUnisonTiesCanBeMatchedInReverseOrder() {
        val score = parse("""<part><measure>
          ${note("start", number = 1)}${note("start", number = 2, chord = true)}
          ${note("stop", number = 2)}${note("stop", number = 1, chord = true)}
        </measure></part>""")
        assertEquals(listOf(0L to 8L, 0L to 8L), score.notes.map { it.startTick to it.durationTick })
    }

    @Test
    fun unnumberedAmbiguousUnisonTiesReportAnImportError() {
        val error = assertFailsWith<IllegalArgumentException> {
            parse("<part><measure>${note("start")}${note("start", chord = true)}${note("stop")}</measure></part>")
        }
        assertTrue(error.message.orEmpty().contains("Ambiguous MusicXML tie"))
    }

    @Test
    fun enharmonicSameMidiPitchDoesNotSilentlyChangeTieIdentity() {
        val start = note("start").replace("<step>C</step>", "<step>C</step><alter>1</alter>")
        val stop = note("stop").replace("<step>C</step>", "<step>D</step><alter>-1</alter>")
        assertEquals(2, parse("<part><measure>$start$stop</measure></part>").notes.size)
    }

    private fun note(tie: String, voice: Int = 1, staff: Int = 1, duration: Int = 4,
        number: Int? = null, chord: Boolean = false, extraTie: String? = null) = """
        <note>${if (chord) "<chord/>" else ""}<pitch><step>C</step><octave>4</octave></pitch>
          <duration>$duration</duration><tie type="$tie"/>${extraTie?.let { "<tie type=\"$it\"/>" } ?: ""}
          <voice>$voice</voice><staff>$staff</staff>
          <notations><tied type="$tie" ${number?.let { "number=\"$it\"" } ?: ""}/></notations>
        </note>
    """
    private fun parse(parts: String) = MusicXmlParser.parseXml("<score-partwise>$parts</score-partwise>".toByteArray())
}
