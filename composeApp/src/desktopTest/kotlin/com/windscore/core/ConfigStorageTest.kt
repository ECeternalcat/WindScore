package com.windscore.core

import com.windscore.ViewMode
import com.windscore.core.model.AppConfig
import com.windscore.core.model.ThemeMode
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigStorageTest {

    private fun tempDir() = Files.createTempDirectory("config_").toFile()

    @Test
    fun loadReturnsDefaultWhenMissing() {
        val dir = tempDir()
        try {
            assertEquals(AppConfig(), ConfigStorage.load(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun saveThenLoadRoundTrips() {
        val dir = tempDir()
        try {
            val cfg = AppConfig(
                themeMode = ThemeMode.DARK,
                defaultViewMode = ViewMode.ROLL,
                defaultCountdownSeconds = 5,
            )
            ConfigStorage.save(cfg, dir)
            assertEquals(cfg, ConfigStorage.load(dir))
        } finally {
            dir.deleteRecursively()
        }
    }
}
