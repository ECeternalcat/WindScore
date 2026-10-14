package com.windscore.core.semantics

import com.windscore.core.RecentsStorage
import com.windscore.core.ScoreCacheEnvelope
import com.windscore.core.calculateMd5
import com.windscore.core.model.Score
import com.windscore.core.parser.MusicXmlParser
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class DivisionsNormalizationTest {
    @Test
    fun coprimeResolutionsNormalizeNotesRestsBackupAndForwardExactly() {
        val score = parse("""<part id="P1"><measure>
          <attributes><divisions>3</divisions></attributes>
          <note><rest/><duration>3</duration></note>
          ${note(3)}<backup><duration>6</duration></backup>
          <attributes><divisions>5</divisions></attributes>
          <forward><duration>5</duration></forward>${note(5)}
        </measure></part>""")
        assertEquals(15, score.divisions)
        assertEquals(listOf(15L, 15L), score.notes.map { it.startTick })
        assertEquals(listOf(15L, 15L), score.notes.map { it.durationTick })
        assertEquals(30L, score.totalTicks)
        assertFalse(score.legacyTimingCompatible)
    }

    @Test
    fun divisionsAreIndependentBetweenPartsIncludingTheImplicitDefault() {
        val score = parse("""<part id="P1"><measure><attributes><divisions>3</divisions></attributes>${note(3)}</measure></part>
          <part id="P2"><measure>${note(4)}</measure></part>""")
        assertEquals(12, score.divisions)
        assertEquals(listOf(12L, 12L), score.notes.map { it.durationTick })
        assertEquals(listOf(0L, 0L), score.notes.map { it.startTick })
        assertFalse(score.legacyTimingCompatible)
    }

    @Test
    fun uniformResolutionRetainsExistingCoordinatesAndIds() {
        val score = parse("""<part id="P1"><measure><attributes><divisions>3</divisions></attributes>${note(3)}${note(3)}</measure></part>""")
        assertEquals(3, score.divisions)
        assertEquals(listOf(0L, 3L), score.notes.map { it.startTick })
        assertEquals(listOf(0, 1), score.notes.map { it.id })
        assertTrue(score.legacyTimingCompatible)
    }

    @Test
    fun invalidOrExcessiveResolutionsAreRejectedInsteadOfTruncated() {
        for (d in listOf(0, -1)) {
            assertFailsWith<IllegalArgumentException> { parse("<part><measure><attributes><divisions>$d</divisions></attributes>${note(4)}</measure></part>") }
        }
        assertFailsWith<IllegalArgumentException> { parse("""<part><measure>
          <attributes><divisions>65537</divisions></attributes>${note(1)}
          <attributes><divisions>65539</divisions></attributes>${note(1)}
        </measure></part>""") }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun oldParsedCacheIsRebuiltWithoutChangingXmlIdentity() {
        val dir = Files.createTempDirectory("semantics_cache").toFile()
        try {
            val xml = "<score-partwise><part><measure><attributes><divisions>4</divisions></attributes>${note(4)}<attributes><divisions>8</divisions></attributes>${note(8)}</measure></part></score-partwise>"
            val score = MusicXmlParser.parseXml(xml.toByteArray())
            RecentsStorage.save(listOf(score), dir)
            val file = File(dir, "scores/${score.id}.cache")
            val envelope = Cbor.decodeFromByteArray(ScoreCacheEnvelope.serializer(), file.readBytes())
            file.writeBytes(Cbor.encodeToByteArray(ScoreCacheEnvelope.serializer(), envelope.copy(
                score = envelope.score.copy(version = 1, notes = emptyList()),
            )))
            val restored = RecentsStorage.load(dir).single()
            assertEquals(calculateMd5(xml), restored.id)
            assertEquals(Score.CURRENT_VERSION, restored.version)
            assertEquals(listOf(0L, 8L), restored.notes.map { it.startTick })
            assertEquals(xml, File(dir, "scores/${score.id}.xml").readText())
        } finally { dir.deleteRecursively() }
    }

    private fun note(duration: Int) = "<note><pitch><step>C</step><octave>4</octave></pitch><duration>$duration</duration></note>"
    private fun parse(parts: String) = MusicXmlParser.parseXml("<score-partwise>$parts</score-partwise>".toByteArray())
}
