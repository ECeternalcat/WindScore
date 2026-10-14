package com.windscore.core.semantics

import com.windscore.core.RecentsStorage
import com.windscore.core.ScoreCacheEnvelope
import com.windscore.core.model.TempoChange
import com.windscore.core.parser.MusicXmlParser
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class TempoImportTest {
    @Test
    fun decimalAndFractionalOffsetsHaveExactCanonicalCoordinates() {
        val score = parse("""<part><measure><attributes><divisions>4</divisions></attributes>
          <direction><offset sound="yes">0.5</offset><sound tempo="60"/></direction>${note(4)}
          <direction><offset sound="yes">-0.5</offset><sound tempo="90"/></direction>
        </measure></part>""")
        assertEquals(8, score.divisions)
        assertEquals(listOf(TempoChange(1, 60.0), TempoChange(7, 90.0)), score.tempoChanges)
        assertEquals(8L, score.notes.single().durationTick)
        assertFalse(score.legacyTimingCompatible)
        val whole = parse("<part><measure><direction><offset sound=\"yes\">4.0</offset><sound tempo=\"60\"/></direction>${note(4)}</measure></part>")
        assertEquals(listOf(TempoChange(4, 60.0)), whole.tempoChanges)
        assertEquals(4, whole.divisions)
    }

    @Test
    fun decimalTempoAndNormalizedSoundOffsetsArePreserved() {
        val score = parse("""<part><measure><attributes><divisions>4</divisions></attributes>
          <sound tempo="120.5"/>${note(4)}<attributes><divisions>8</divisions></attributes>
          <direction><offset sound="yes">4</offset><sound tempo="60"/></direction>${note(8)}
        </measure></part>""")
        assertEquals(listOf(TempoChange(0, 120.5), TempoChange(12, 60.0)), score.tempoChanges)
        assertEquals(121, score.tempo)
        assertEquals(listOf(0L, 8L), score.notes.map { it.startTick })
    }

    @Test
    fun visualOffsetDoesNotDelaySoundAndSoundOffsetOverridesDirectionOffset() {
        val visual = parse("<part><measure>${note(4)}<direction><offset>4</offset><sound tempo=\"60\"/></direction></measure></part>")
        assertEquals(listOf(TempoChange(4, 60.0)), visual.tempoChanges)
        assertEquals(120, visual.tempo)
        val explicit = parse("""<part><measure>${note(4)}<direction>
          <offset sound="yes">4</offset><sound tempo="60"><offset>2</offset></sound>
        </direction></measure></part>""")
        assertEquals(listOf(TempoChange(6, 60.0)), explicit.tempoChanges)
    }

    @Test
    fun matchingPartTemposAreDeduplicatedButConflictsAreReported() {
        val part = "<part><measure><sound tempo=\"120\"/>${note(4)}</measure></part>"
        assertEquals(listOf(TempoChange(0, 120.0)), parse(part + part).tempoChanges)
        assertFailsWith<IllegalArgumentException> { parse(part + part.replace("120", "60")) }
    }

    @Test
    fun lastDeclarationAtSamePositionInOnePartWins() {
        assertEquals(listOf(TempoChange(0, 60.0)), parse("<part><measure><sound tempo=\"120\"/><sound tempo=\"60\"/>${note(4)}</measure></part>").tempoChanges)
    }

    @Test
    fun unsupportedTempoRequestsAndOffsetsDoNotSilentlyProduceAFixedTempo() {
        for (tempo in listOf("0", "-1", "NaN", "Infinity", "invalid")) {
            assertFailsWith<IllegalArgumentException> { parse("<part><measure><sound tempo=\"$tempo\"/>${note(4)}</measure></part>") }
        }
        assertFailsWith<IllegalArgumentException> { parse("<part><measure><direction><offset sound=\"yes\">-4</offset><sound tempo=\"60\"/></direction>${note(4)}</measure></part>") }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun preTempoMapCacheIsRebuiltAndNewMapRoundTripsUnderTheSameSourceId() {
        val dir = Files.createTempDirectory("tempo_cache").toFile()
        try {
            val score = parse("<part><measure><sound tempo=\"120\"/>${note(4)}<sound tempo=\"60\"/>${note(4)}</measure></part>")
            RecentsStorage.save(listOf(score), dir)
            val file = File(dir, "scores/${score.id}.cache")
            val envelope = Cbor.decodeFromByteArray(ScoreCacheEnvelope.serializer(), file.readBytes())
            file.writeBytes(Cbor.encodeToByteArray(ScoreCacheEnvelope.serializer(), envelope.copy(
                score = envelope.score.copy(version = 3, tempoChanges = emptyList()),
            )))
            val restored = RecentsStorage.load(dir).single()
            assertEquals(score.id, restored.id)
            assertEquals(score.tempoChanges, restored.tempoChanges)
            assertEquals(score.tempoChanges, RecentsStorage.load(dir).single().tempoChanges)
        } finally { dir.deleteRecursively() }
    }

    private fun note(duration: Int) = "<note><pitch><step>C</step><octave>4</octave></pitch><duration>$duration</duration></note>"
    private fun parse(parts: String) = MusicXmlParser.parseXml("<score-partwise>$parts</score-partwise>".toByteArray())
}
