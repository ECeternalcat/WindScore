package com.windscore.core

import com.windscore.core.model.AppConfig
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Desktop / Android 共享的全局配置持久化实现。
 */
internal object ConfigStorage {

    private val configFile: File get() = File(getAppStorageDir(), "app_config.json")

    fun load(): AppConfig {
        val file = configFile
        return try {
            if (file.exists()) {
                val jsonText = file.readText(Charsets.UTF_8)
                Json.decodeFromString(AppConfig.serializer(), jsonText)
            } else {
                AppConfig()
            }
        } catch (e: Exception) {
            println("[ConfigStorage] Failed to load config: ${e.message}")
            AppConfig()
        }
    }

    fun save(config: AppConfig) {
        try {
            val dir = File(getAppStorageDir())
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = configFile
            val jsonText = Json.encodeToString(AppConfig.serializer(), config)
            file.writeText(jsonText, Charsets.UTF_8)
        } catch (e: Exception) {
            println("[ConfigStorage] Failed to save config: ${e.message}")
        }
    }
}
