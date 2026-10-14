package com.windscore.core

import android.content.Context
import com.windscore.core.model.Score
import com.windscore.core.model.ScoreRecord
import java.io.File
import java.security.MessageDigest

object AndroidContext {
    @Volatile
    var context: Context? = null
}

internal fun requireAndroidContext(): Context =
    AndroidContext.context ?: throw IllegalStateException("Android Context not initialized")

actual fun getAppStorageDir(): String {
    val ctx = requireAndroidContext()
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

actual fun loadAppConfig(): com.windscore.core.model.AppConfig {
    return ConfigStorage.load()
}

actual fun saveAppConfig(config: com.windscore.core.model.AppConfig) {
    ConfigStorage.save(config)
}

actual fun loadAppLanguage(default: String): String = LangStorage.load(default)

actual fun saveAppLanguage(code: String) {
    LangStorage.save(code)
}

actual fun nowEpochMillis(): Long = DateTimeUtil.nowEpochMillis()

actual fun formatDateTime(epochMillis: Long): String = DateTimeUtil.formatDateTime(epochMillis)

actual fun loadScoreHistory(scoreId: String): List<ScoreRecord> = ScoreHistoryStorage.load(scoreId)

actual fun appendScoreRecord(scoreId: String, record: ScoreRecord): List<ScoreRecord> =
    ScoreHistoryStorage.append(scoreId, record)

actual fun savePracticeAttempt(attempt: com.windscore.core.practice.PracticeAttempt) {
    AttemptStorage.save(attempt)
}

actual fun loadPracticeAttempts(scoreId: String): List<com.windscore.core.practice.PracticeAttempt> =
    AttemptStorage.load(scoreId)

actual fun cleanupAttemptTempFiles(): Int = AttemptStorage.cleanupTempFiles()
