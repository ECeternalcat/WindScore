package com.windscore.core

import android.content.Context
import com.windscore.core.model.Score
import com.windscore.core.parser.MusicXmlParser
import java.io.File
import java.security.MessageDigest

object AndroidContext {
    @Volatile
    var context: Context? = null
}

actual fun getAppStorageDir(): String {
    val ctx = AndroidContext.context ?: throw IllegalStateException("Android Context not initialized")
    val dir = ctx.filesDir
    if (!dir.exists()) dir.mkdirs()
    return dir.absolutePath
}

actual fun calculateMd5(text: String): String {
    val md = MessageDigest.getInstance("MD5")
    val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

actual fun saveRecentScores(scores: List<Score>) {
    try {
        val scoresDir = File(getScoresDir())
        if (!scoresDir.exists()) scoresDir.mkdirs()

        // 1. Write each score XML to scores/xmlHash.xml
        for (score in scores) {
            val xml = score.rawXml ?: continue
            val xmlHash = calculateMd5(xml)
            val scoreFile = File(scoresDir, "$xmlHash.xml")
            if (!scoreFile.exists()) {
                scoreFile.writeText(xml, Charsets.UTF_8)
            }
        }

        // 2. Write recents.json
        val sb = java.lang.StringBuilder()
        sb.append("[\n")
        var first = true
        for (score in scores) {
            val xml = score.rawXml ?: continue
            val xmlHash = calculateMd5(xml)
            val titleEsc = score.title.replace("\"", "\\\"")
            if (!first) sb.append(",\n")
            sb.append("  {\"title\":\"").append(titleEsc).append("\",\"xmlHash\":\"").append(xmlHash).append("\"}")
            first = false
        }
        sb.append("\n]")

        val recentsFile = File(getAppStorageDir(), "recents.json")
        recentsFile.writeText(sb.toString(), Charsets.UTF_8)
        println("[Storage] Saved recent scores list successfully.")
    } catch (e: Exception) {
        println("[Storage] Failed to save recent scores: ${e.message}")
    }
}

actual fun loadRecentScores(onLoaded: (List<Score>) -> Unit) {
    try {
        val recentsFile = File(getAppStorageDir(), "recents.json")
        if (!recentsFile.exists()) {
            onLoaded(emptyList())
            return
        }
        val json = recentsFile.readText(Charsets.UTF_8)
        val itemPattern = "\\{\\s*\"title\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"xmlHash\"\\s*:\\s*\"([^\"]+)\"\\s*\\}".toRegex()
        val items = itemPattern.findAll(json).map { match ->
            val title = match.groupValues[1]
            val xmlHash = match.groupValues[2]
            Pair(title, xmlHash)
        }.toList()

        val scoresDir = File(getScoresDir())
        val result = mutableListOf<Score>()
        for (item in items) {
            val scoreFile = File(scoresDir, "${item.second}.xml")
            if (scoreFile.exists()) {
                val xmlBytes = scoreFile.readBytes()
                val score = MusicXmlParser.parse(xmlBytes)
                val finalScore = score.copy(title = item.first, rawXml = String(xmlBytes, Charsets.UTF_8))
                result.add(finalScore)
            }
        }
        onLoaded(result)
        println("[Storage] Loaded ${result.size} recent scores from local storage.")
    } catch (e: Exception) {
        println("[Storage] Failed to load recent scores: ${e.message}")
        onLoaded(emptyList())
    }
}
