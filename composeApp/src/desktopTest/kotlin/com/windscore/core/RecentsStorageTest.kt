package com.windscore.core

import com.windscore.core.model.NoteEntity
import com.windscore.core.model.Score
import com.windscore.core.model.TrackType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RecentsStorageTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun savingFullScoreMakesItAvailableForSheetModeAfterReload() {
        val dir = Files.createTempDirectory("recents_").toFile()
        try {
            val xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise><part id="P1"><measure number="1">
                  <attributes><divisions>4</divisions></attributes>
                  <note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration><staff>1</staff></note>
                </measure></part></score-partwise>
            """.trimIndent()
            val score = Score(
                id = calculateMd5(xml),
                notes = listOf(NoteEntity(0, 60, TrackType.RIGHT_HAND, 0, 4)),
                divisions = 4,
                tempo = 120,
                totalTicks = 4,
                rawXml = xml,
                title = "Sheet score",
            )

            RecentsStorage.save(listOf(score), dir)
            val reloaded = RecentsStorage.load(dir).singleOrNull()

            assertNotNull(reloaded)
            assertEquals(score.id, reloaded.id)
            assertEquals(score.notes, reloaded.notes)
            assertTrue(reloaded.supportsSheetView)
            assertEquals(xml, File(dir, "scores/${score.id}.xml").readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun loadingLegacyScoreCacheMigratesItToCurrentSchema() {
        val dir = Files.createTempDirectory("recents_legacy_").toFile()
        try {
            val score = score("Legacy score")
            RecentsStorage.save(listOf(score), dir)

            val cacheFile = File(dir, "scores/${score.id}.cache")
            val legacyCache = Cbor.encodeToByteArray(
                Score.serializer(),
                score.copy(
                    fingerprint = File(dir, "scores/${score.id}.xml").let {
                        com.windscore.core.model.Fingerprint(it.length(), it.lastModified())
                    },
                    version = Score.CURRENT_VERSION,
                    supportsSheetView = true,
                ),
            )
            cacheFile.atomicWriteBytes(legacyCache)

            assertEquals(score.id, RecentsStorage.load(dir).single().id)
            val migrated = Cbor.decodeFromByteArray(ScoreCacheEnvelope.serializer(), cacheFile.readBytes())
            assertEquals(ScoreCacheEnvelope.CURRENT_SCHEMA_VERSION, migrated.schemaVersion)
        } finally {
            dir.deleteRecursively()
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun loadingUnknownCacheSchemaReparsesAndRewritesIt() {
        val dir = Files.createTempDirectory("recents_schema_").toFile()
        try {
            val score = score("Schema score")
            RecentsStorage.save(listOf(score), dir)

            val cacheFile = File(dir, "scores/${score.id}.cache")
            val current = Cbor.decodeFromByteArray(ScoreCacheEnvelope.serializer(), cacheFile.readBytes())
            cacheFile.atomicWriteBytes(
                Cbor.encodeToByteArray(
                    ScoreCacheEnvelope.serializer(),
                    current.copy(schemaVersion = ScoreCacheEnvelope.CURRENT_SCHEMA_VERSION + 1),
                ),
            )

            assertEquals(score.id, RecentsStorage.load(dir).single().id)
            val rewritten = Cbor.decodeFromByteArray(ScoreCacheEnvelope.serializer(), cacheFile.readBytes())
            assertEquals(ScoreCacheEnvelope.CURRENT_SCHEMA_VERSION, rewritten.schemaVersion)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun recentTitleWithJsonSpecialCharactersRoundTrips() {
        val dir = Files.createTempDirectory("recents_json_").toFile()
        try {
            val title = "Quoted \"title\"\\folder\nnext line\tend"
            RecentsStorage.save(listOf(score(title)), dir)

            assertEquals(title, RecentsStorage.load(dir).single().title)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun score(title: String): Score {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <score-partwise><part id="P1"><measure number="1">
              <attributes><divisions>4</divisions></attributes>
              <note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration><staff>1</staff></note>
            </measure></part></score-partwise>
        """.trimIndent()
        return Score(
            id = calculateMd5(xml),
            notes = listOf(NoteEntity(0, 60, TrackType.RIGHT_HAND, 0, 4)),
            divisions = 4,
            tempo = 120,
            totalTicks = 4,
            rawXml = xml,
            title = title,
        )
    }
}
