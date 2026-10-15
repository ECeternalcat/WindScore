package com.windscore.core

import com.windscore.core.model.ScoreSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsCompatibilityTest {
    @Test
    fun firstVersionedSaveRetainsOriginalUnversionedSettings() {
        val dir = Files.createTempDirectory("legacy_positions").toFile()
        try {
            val settingsDir = File(dir, "settings").apply { mkdirs() }
            val legacy = """{"currentTick":4,"loopStartTick":4,"loopEndTick":12,"speedMultiplier":0.5}"""
            File(settingsDir, "score.json").writeText(legacy)
            assertEquals(4L, SettingsStorage.load("score", dir).currentTick)
            SettingsStorage.save("score", ScoreSettings(scoreVersion = 2, divisions = 8), dir)
            assertEquals(legacy, File(settingsDir, "score.legacy.json").readText())
            SettingsStorage.save("score", ScoreSettings(scoreVersion = 2, divisions = 8, currentTick = 8), dir)
            assertEquals(legacy, File(settingsDir, "score.legacy.json").readText())
            assertEquals(8L, SettingsStorage.load("score", dir).currentTick)
        } finally { dir.deleteRecursively() }
    }
}
