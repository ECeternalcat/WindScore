package com.windscore.core

import com.windscore.core.model.Fingerprint
import com.windscore.core.model.Score
import com.windscore.core.parser.MusicXmlParser
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.json.Json
import java.io.File

/** Versioned on-disk envelope for parsed-score CBOR caches. */
@Serializable
internal data class ScoreCacheEnvelope(
    val schemaVersion: Int,
    val score: Score,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
private data class RecentScoreEntry(
    val title: String,
    val xmlHash: String,
)

/**
 * Desktop / Android 共享的最近乐谱持久化实现。
 *
 * 负责 recents.json 的写入/读取以及对应 XML 文件的落地。平台差异（[getAppStorageDir] /
 * [calculateMd5]）通过 commonMain 的 expect 在各 target 解析到对应 actual。
 */
@OptIn(ExperimentalSerializationApi::class)
internal object RecentsStorage {

    private val json = Json { ignoreUnknownKeys = true }
    private val recentEntriesSerializer = ListSerializer(RecentScoreEntry.serializer())

    private fun scoresDir(baseDir: File) = File(baseDir, "scores")
    private fun recentsFile(baseDir: File) = File(baseDir, "recents.json")

    fun save(scores: List<Score>, baseDir: File = File(getAppStorageDir())) {
        try {
            val scoresDir = scoresDir(baseDir)
            val recentsFile = recentsFile(baseDir)
            if (!scoresDir.exists()) scoresDir.mkdirs()

            // 1. 有原始 XML 时更新落地文件和 CBOR 缓存；轻量条目复用已有文件。
            for (score in scores) {
                val xml = score.rawXml
                val xmlHash = score.id.takeIf { it.isNotEmpty() }
                    ?: xml?.let(::calculateMd5)
                    ?: continue
                if (xml == null) continue
                val scoreFile = File(scoresDir, "$xmlHash.xml")
                if (!scoreFile.exists()) {
                    scoreFile.atomicWriteText(xml)
                }
                
                val cacheFile = File(scoresDir, "$xmlHash.cache")
                val fingerprint = Fingerprint(scoreFile.length(), scoreFile.lastModified())
                val scoreToCache = score.copy(
                    id = xmlHash,
                    fingerprint = fingerprint,
                    version = Score.CURRENT_VERSION,
                    supportsSheetView = true,
                )
                try {
                    writeCache(cacheFile, scoreToCache)
                } catch (e: Exception) {
                    println("[Storage] Failed to cache score: ${e.message}")
                }
            }

            // 2. 写入轻量的最近乐谱索引。
            val recentEntries = scores.mapNotNull { score ->
                val xmlHash = score.id.takeIf { it.isNotEmpty() }
                    ?: score.rawXml?.let(::calculateMd5)
                    ?: return@mapNotNull null
                RecentScoreEntry(title = score.title, xmlHash = xmlHash)
            }
            recentsFile.atomicWriteText(json.encodeToString(recentEntriesSerializer, recentEntries))
            println("[Storage] Saved recent scores list successfully.")
        } catch (e: Exception) {
            println("[Storage] Failed to save recent scores: ${e.message}")
        }
    }

    fun load(baseDir: File = File(getAppStorageDir())): List<Score> {
        return try {
            val scoresDir = scoresDir(baseDir)
            val recentsFile = recentsFile(baseDir)
            if (!recentsFile.exists()) return emptyList()
            val items = json.decodeFromString(
                recentEntriesSerializer,
                recentsFile.readText(Charsets.UTF_8),
            )

            val result = mutableListOf<Score>()
            for (item in items) {
                val title = item.title
                val scoreId = item.xmlHash
                val xmlFile = File(scoresDir, "$scoreId.xml")
                val cacheFile = File(scoresDir, "$scoreId.cache")
                
                if (!xmlFile.exists()) continue

                // 1. 尝试从 CBOR 缓存命中
                var cachedScore: Score? = null
                if (cacheFile.exists()) {
                    try {
                        val cached = readCache(cacheFile)
                        val score = cached.score
                        val currentFingerprint = Fingerprint(xmlFile.length(), xmlFile.lastModified())
                        if (score.fingerprint == currentFingerprint && score.version == Score.CURRENT_VERSION) {
                            cachedScore = score.copy(
                                title = title,
                                rawXml = null,
                                supportsSheetView = true,
                            )
                            if (cached.isLegacy) {
                                writeCache(cacheFile, score)
                            }
                        } else {
                            println("[Storage] Cache fingerprint mismatch for $title, re-parsing...")
                        }
                    } catch (e: Exception) {
                        println("[Storage] Failed to read cache for $title: ${e.message}")
                        cacheFile.delete()
                    }
                }

                if (cachedScore != null) {
                    result.add(cachedScore)
                } else {
                    // 2. 降级：重新解析 XML
                    val xmlBytes = xmlFile.readBytes()
                    val parsedScore = MusicXmlParser.parse(xmlBytes)
                    val fingerprint = Fingerprint(xmlFile.length(), xmlFile.lastModified())
                    val finalScore = parsedScore.copy(
                        id = scoreId,
                        title = title,
                        fingerprint = fingerprint,
                        version = Score.CURRENT_VERSION,
                        rawXml = null, // 列表保持轻量
                        supportsSheetView = true,
                    )
                    result.add(finalScore)

                    // 3. 写入缓存文件
                    try {
                        writeCache(cacheFile, finalScore)
                    } catch (e: Exception) {
                        println("[Storage] Failed to write cache: ${e.message}")
                    }
                }
            }
            println("[Storage] Loaded ${result.size} recent scores from local storage.")
            result
        } catch (e: Exception) {
            println("[Storage] Failed to load recent scores: ${e.message}")
            emptyList()
        }
    }

    private data class CachedScore(val score: Score, val isLegacy: Boolean)

    private fun readCache(cacheFile: File): CachedScore {
        val bytes = cacheFile.readBytes()
        val envelope = runCatching {
            Cbor.decodeFromByteArray(ScoreCacheEnvelope.serializer(), bytes)
        }.getOrNull()
        if (envelope != null) {
            require(envelope.schemaVersion == ScoreCacheEnvelope.CURRENT_SCHEMA_VERSION) {
                "Unsupported score cache schema ${envelope.schemaVersion}"
            }
            return CachedScore(envelope.score, isLegacy = false)
        }
        val legacyScore = Cbor.decodeFromByteArray(Score.serializer(), bytes)
        return CachedScore(legacyScore, isLegacy = true)
    }

    private fun writeCache(cacheFile: File, score: Score) {
        val envelope = ScoreCacheEnvelope(ScoreCacheEnvelope.CURRENT_SCHEMA_VERSION, score)
        cacheFile.atomicWriteBytes(Cbor.encodeToByteArray(ScoreCacheEnvelope.serializer(), envelope))
    }

}
