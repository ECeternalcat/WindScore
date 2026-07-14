package com.windscore.core

import java.io.File

/**
 * 应用级语言偏好的持久化（Desktop / Android 共享）。
 * 存储为 `<appStorageDir>/lang.txt`，内容为语言代码（en/zh/ja）。
 */
internal object LangStorage {
    private val file: File get() = File(getAppStorageDir(), "lang.txt")

    fun load(default: String): String {
        val f = file
        if (!f.exists()) return default
        val code = runCatching { f.readText(Charsets.UTF_8).trim() }.getOrNull()
        return if (code.isNullOrEmpty()) default else code
    }

    fun save(code: String) {
        runCatching {
            val dir = File(getAppStorageDir())
            if (!dir.exists()) dir.mkdirs()
            file.writeText(code, Charsets.UTF_8)
        }
    }
}
