package com.windscore.core

import com.windscore.core.model.Fingerprint
import com.windscore.core.model.Score
import com.windscore.core.parser.MusicXmlParser
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import java.io.File

/**
 * Desktop / Android 共享的最近乐谱持久化实现。
 *
 * 负责 recents.json 的写入/读取以及对应 XML 文件的落地。平台差异（[getAppStorageDir] /
 * [calculateMd5]）通过 commonMain 的 expect 在各 target 解析到对应 actual。
 */
@OptIn(ExperimentalSerializationApi::class)
internal object RecentsStorage {

    private val scoresDir: File get() = File(getScoresDir())
    private val recentsFile: File get() = File(getAppStorageDir(), "recents.json")

    fun save(scores: List<Score>) {
        try {
            if (!scoresDir.exists()) scoresDir.mkdirs()

            // 1. 落地每个乐谱的原始 XML 和 CBOR 缓存
            for (score in scores) {
                val xml = score.rawXml ?: continue
                val xmlHash = score.id.takeIf { it.isNotEmpty() } ?: calculateMd5(xml)
                val scoreFile = File(scoresDir, "$xmlHash.xml")
                if (!scoreFile.exists()) {
                    scoreFile.writeText(xml, Charsets.UTF_8)
                }
                
                val cacheFile = File(scoresDir, "$xmlHash.cache")
                val fingerprint = Fingerprint(scoreFile.length(), scoreFile.lastModified())
                val scoreToCache = score.copy(
                    id = xmlHash,
                    fingerprint = fingerprint,
                    version = Score.CURRENT_VERSION
                )
                try {
                    val cborBytes = Cbor.encodeToByteArray(Score.serializer(), scoreToCache)
                    cacheFile.writeBytes(cborBytes)
                } catch (e: Exception) {
                    println("[Storage] Failed to cache score: ${e.message}")
                }
            }

            // 2. 写入 recents.json（使用完整的 JSON 字符串转义）
            val sb = java.lang.StringBuilder()
            sb.append("[\n")
            var first = true
            for (score in scores) {
                val xmlHash = score.id.takeIf { it.isNotEmpty() } ?: score.rawXml?.let { calculateMd5(it) } ?: continue
                if (!first) sb.append(",\n")
                sb.append("  {\"title\":\"").append(escapeJson(score.title))
                    .append("\",\"xmlHash\":\"").append(escapeJson(xmlHash))
                    .append("\"}")
                first = false
            }
            sb.append("\n]")

            recentsFile.writeText(sb.toString(), Charsets.UTF_8)
            println("[Storage] Saved recent scores list successfully.")
        } catch (e: Exception) {
            println("[Storage] Failed to save recent scores: ${e.message}")
        }
    }

    fun load(): List<Score> {
        return try {
            if (!recentsFile.exists()) return emptyList()
            val json = recentsFile.readText(Charsets.UTF_8)
            val items = parseRecentsJson(json)

            val result = mutableListOf<Score>()
            for (item in items) {
                val title = item.first
                val scoreId = item.second
                val xmlFile = File(scoresDir, "$scoreId.xml")
                val cacheFile = File(scoresDir, "$scoreId.cache")
                
                if (!xmlFile.exists()) continue

                // 1. 尝试从 CBOR 缓存命中
                var cachedScore: Score? = null
                if (cacheFile.exists()) {
                    try {
                        val bytes = cacheFile.readBytes()
                        val score = Cbor.decodeFromByteArray(Score.serializer(), bytes)
                        val currentFingerprint = Fingerprint(xmlFile.length(), xmlFile.lastModified())
                        if (score.fingerprint == currentFingerprint && score.version == Score.CURRENT_VERSION) {
                            cachedScore = score.copy(title = title, rawXml = null)
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
                        rawXml = null // 列表保持轻量
                    )
                    result.add(finalScore)

                    // 3. 写入缓存文件
                    try {
                        val cborBytes = Cbor.encodeToByteArray(Score.serializer(), finalScore)
                        cacheFile.writeBytes(cborBytes)
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

    /** 完整的 JSON 字符串转义，避免标题中的换行/制表/反斜杠损坏 recents.json。 */
    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    /**
     * 解析 recents.json：数组内每个对象形如 {"title":"...","xmlHash":"..."}。
     * 手写解析以正确处理转义字符（旧的 [^\"]+ 正则遇到转义引号会截断）。
     */
    private fun parseRecentsJson(json: String): List<Pair<String, String>> {
        val items = mutableListOf<Pair<String, String>>()
        val titleKey = "\"title\""
        val hashKey = "\"xmlHash\""
        var i = 0
        while (i < json.length) {
            val titleIdx = json.indexOf(titleKey, i)
            if (titleIdx < 0) break
            val titleVal = readStringAfterKey(json, titleIdx + titleKey.length) ?: break
            val hashIdx = json.indexOf(hashKey, titleVal.second)
            if (hashIdx < 0) break
            val hashVal = readStringAfterKey(json, hashIdx + hashKey.length) ?: break
            items.add(titleVal.first to hashVal.first)
            i = hashVal.second
        }
        return items
    }

    /** 在 key 之后跳过冒号与空白，读取一个 JSON 字符串字面量，返回 (解码值, 闭引号后下标)。 */
    private fun readStringAfterKey(json: String, start: Int): Pair<String, Int>? {
        var p = start
        while (p < json.length && (json[p] == ':' || json[p].isWhitespace())) p++
        if (p >= json.length || json[p] != '"') return null
        return readJsonString(json, p)
    }

    /** 读取从 openQuoteIndex（指向开引号）开始的 JSON 字符串字面量。 */
    private fun readJsonString(json: String, openQuoteIndex: Int): Pair<String, Int>? {
        val sb = StringBuilder()
        var i = openQuoteIndex + 1
        while (i < json.length) {
            val c = json[i]
            if (c == '\\' && i + 1 < json.length) {
                when (val n = json[i + 1]) {
                    '\\' -> sb.append('\\')
                    '"' -> sb.append('"')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    else -> { sb.append('\\'); sb.append(n) }
                }
                i += 2
            } else if (c == '"') {
                return sb.toString() to (i + 1)
            } else {
                sb.append(c)
                i++
            }
        }
        return null
    }
}
