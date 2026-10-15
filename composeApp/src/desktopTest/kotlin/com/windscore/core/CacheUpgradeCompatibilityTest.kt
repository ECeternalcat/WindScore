package com.windscore.core

import com.windscore.core.model.Score
import com.windscore.core.parser.MusicXmlParser
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class CacheUpgradeCompatibilityTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun semanticUpgradeArchivesThePreviousModelWithoutOverwritingItsCoordinates() {
        val dir = Files.createTempDirectory("historical_score").toFile()
        try {
            val xml = "<score-partwise><part><measure>${note()}</measure></part></score-partwise>"
            val current = MusicXmlParser.parseXml(xml.toByteArray())
            val legacy = current.copy(version = 1, notes = current.notes.map { it.copy(startTick = 3) })
            RecentsStorage.save(listOf(legacy), dir)
            val cache = File(dir, "scores/${current.id}.cache")
            val original = cache.readBytes()
            val restored = RecentsStorage.load(dir).single()
            val archive = File(dir, "scores/${current.id}.cache.v1")
            assertContentEquals(original, archive.readBytes())
            assertEquals(3L, Cbor.decodeFromByteArray(ScoreCacheEnvelope.serializer(), archive.readBytes()).score.notes.single().startTick)
            assertEquals(0L, restored.notes.single().startTick)
            assertEquals(current.id, restored.id)
            assertEquals(Score.CURRENT_VERSION, restored.version)
            RecentsStorage.save(listOf(current), dir)
            assertContentEquals(original, archive.readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun oneAmbiguousLegacyImportDoesNotHideValidScoresOrDeleteItsSource() {
        val dir = Files.createTempDirectory("partial_cache_upgrade").toFile()
        try {
            val valid = MusicXmlParser.parseXml("<score-partwise><part><measure>${note()}</measure></part></score-partwise>".toByteArray())
            val ambiguous = "<score-partwise><part><measure>${note("start")}${note("start", true)}${note("stop")}</measure></part></score-partwise>"
            val badId = calculateMd5(ambiguous)
            val legacy = Score(emptyList(), 4, 120, 8, id = badId, version = 1, rawXml = ambiguous)
            RecentsStorage.save(listOf(valid, legacy), dir)
            assertEquals(listOf(valid.id), RecentsStorage.load(dir).map { it.id })
            assertEquals(ambiguous, File(dir, "scores/$badId.xml").readText())
            assertTrue(File(dir, "recents.json").readText().contains(badId))
        } finally { dir.deleteRecursively() }
    }

    private fun note(tie: String? = null, chord: Boolean = false) = """
        <note>${if (chord) "<chord/>" else ""}<pitch><step>C</step><octave>4</octave></pitch><duration>4</duration>
          ${tie?.let { "<tie type=\"$it\"/>" } ?: ""}<voice>1</voice><staff>1</staff></note>
    """
}
