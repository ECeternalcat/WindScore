package com.windscore.core

import com.windscore.core.model.ScoreSettings
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Desktop / Android 共享的乐谱个性化配置持久化实现。
 */
internal object SettingsStorage {

    private val settingsDir: File get() = File(getAppStorageDir(), "settings")

    fun load(scoreId: String): ScoreSettings {
        if (scoreId.isEmpty()) return ScoreSettings()
        val file = File(settingsDir, "$scoreId.json")
        return try {
            if (file.exists()) {
                val jsonText = file.readText(Charsets.UTF_8)
                Json.decodeFromString(ScoreSettings.serializer(), jsonText)
            } else {
                val globalConfig = ConfigStorage.load()
                ScoreSettings(
                    viewMode = globalConfig.defaultViewMode,
                    countdownEnabled = globalConfig.defaultCountdownSeconds > 0
                )
            }
        } catch (e: Exception) {
            println("[SettingsStorage] Failed to load settings for $scoreId: ${e.message}")
            ScoreSettings()
        }
    }

    fun save(scoreId: String, settings: ScoreSettings) {
        if (scoreId.isEmpty()) return
        try {
            if (!settingsDir.exists()) {
                settingsDir.mkdirs()
            }
            val file = File(settingsDir, "$scoreId.json")
            val jsonText = Json.encodeToString(ScoreSettings.serializer(), settings)
            file.writeText(jsonText, Charsets.UTF_8)
        } catch (e: Exception) {
            println("[SettingsStorage] Failed to save settings for $scoreId: ${e.message}")
        }
    }
}
