package com.windscore.core

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class LangStorageTest {

    private fun tempDir() = Files.createTempDirectory("lang_").toFile()

    @Test
    fun loadReturnsDefaultWhenMissing() {
        val dir = tempDir()
        try {
            assertEquals("zh", LangStorage.load("zh", dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun saveThenLoadRoundTrips() {
        val dir = tempDir()
        try {
            LangStorage.save("ja", dir)
            // 已保存时应返回保存值，而非默认值
            assertEquals("ja", LangStorage.load("zh", dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun blankContentFallsBackToDefault() {
        val dir = tempDir()
        try {
            LangStorage.save("ja", dir)
            // 模拟文件被清空
            java.io.File(dir, "lang.txt").writeText("   ", Charsets.UTF_8)
            assertEquals("en", LangStorage.load("en", dir))
        } finally {
            dir.deleteRecursively()
        }
    }
}
