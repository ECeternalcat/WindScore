package com.windscore.core

import com.windscore.core.model.Score
import java.io.File
import java.security.MessageDigest

actual fun getAppStorageDir(): String {
    val userHome = System.getProperty("user.home") ?: "."
    val dir = File(userHome, ".windscore")
    if (!dir.exists()) dir.mkdirs()
    return dir.absolutePath
}

actual fun calculateMd5(text: String): String {
    val md = MessageDigest.getInstance("MD5")
    val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

actual fun saveRecentScores(scores: List<Score>) {
    RecentsStorage.save(scores)
}

actual fun loadRecentScores(onLoaded: (List<Score>) -> Unit) {
    onLoaded(RecentsStorage.load())
}

actual fun loadScoreXml(scoreId: String): String? {
    val file = File(getScoresDir(), "$scoreId.xml")
    return if (file.exists()) file.readText(Charsets.UTF_8) else null
}

actual fun loadScoreSettings(scoreId: String): com.windscore.core.model.ScoreSettings {
    return SettingsStorage.load(scoreId)
}

actual fun saveScoreSettings(scoreId: String, settings: com.windscore.core.model.ScoreSettings) {
    SettingsStorage.save(scoreId, settings)
}
