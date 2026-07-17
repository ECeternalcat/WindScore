package com.windscore.core

import java.io.File

/**
 * 应用级语言偏好的持久化（Desktop / Android 共享）。
 * 存储为 `<baseDir>/lang.txt`，内容为语言代码（en/zh/ja）。
 * [baseDir] 仅用于测试注入；生产路径默认为应用存储目录。
 */
internal object LangStorage {
    private fun file(baseDir: File) = File(baseDir, "lang.txt")

    fun load(default: String, baseDir: File = File(getAppStorageDir())): String {
        val f = file(baseDir)
        if (!f.exists()) return default
        val code = runCatching { f.readText(Charsets.UTF_8).trim() }.getOrNull()
        return if (code.isNullOrEmpty()) default else code
    }

    fun save(code: String, baseDir: File = File(getAppStorageDir())) {
        runCatching {
            if (!baseDir.exists()) baseDir.mkdirs()
            file(baseDir).writeText(code, Charsets.UTF_8)
        }
    }
}
