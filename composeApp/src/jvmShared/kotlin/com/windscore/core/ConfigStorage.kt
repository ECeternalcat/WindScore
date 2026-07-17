package com.windscore.core

import com.windscore.core.model.AppConfig
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Desktop / Android 共享的全局配置持久化实现。
 * [baseDir] 仅用于测试注入；生产路径默认为应用存储目录。
 */
internal object ConfigStorage {

    private fun file(baseDir: File) = File(baseDir, "app_config.json")

    fun load(baseDir: File = File(getAppStorageDir())): AppConfig {
        val f = file(baseDir)
        return try {
            if (f.exists()) {
                val jsonText = f.readText(Charsets.UTF_8)
                Json.decodeFromString(AppConfig.serializer(), jsonText)
            } else {
                AppConfig()
            }
        } catch (e: Exception) {
            println("[ConfigStorage] Failed to load config: ${e.message}")
            AppConfig()
        }
    }

    fun save(config: AppConfig, baseDir: File = File(getAppStorageDir())) {
        try {
            if (!baseDir.exists()) {
                baseDir.mkdirs()
            }
            file(baseDir).writeText(Json.encodeToString(AppConfig.serializer(), config), Charsets.UTF_8)
        } catch (e: Exception) {
            println("[ConfigStorage] Failed to save config: ${e.message}")
        }
    }
}
