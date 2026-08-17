package com.windscore.core

import com.windscore.ViewMode
import com.windscore.core.model.AppConfig
import com.windscore.core.model.ScoreSettings
import com.windscore.core.model.ThemeMode
import com.windscore.ui.i18n.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

class AppSettingsControllerTest {
    @Test
    fun initializationAndGlobalUpdatesUseDataSource() = runBlocking {
        val source = FakeDataSource()
        val scope = CoroutineScope(Unconfined + SupervisorJob())
        try {
            val controller = AppSettingsController(scope, source, saveDebounceMs = 0, ioDispatcher = Unconfined)
            eventually { controller.state.value.language == AppLanguage.JAPANESE }

            assertEquals(ThemeMode.DARK, controller.state.value.config.themeMode)
            controller.updateLanguage(AppLanguage.ENGLISH)
            controller.updateConfig(AppConfig(themeMode = ThemeMode.LIGHT))
            eventually { source.savedLanguage == "en" && source.savedConfig?.themeMode == ThemeMode.LIGHT }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun activatingScoreRejectsStaleSettingsResult() = runBlocking {
        val source = FakeDataSource()
        source.blockedScoreId = "a"
        val scope = CoroutineScope(Unconfined + SupervisorJob())
        try {
            val controller = AppSettingsController(scope, source, saveDebounceMs = 0, ioDispatcher = Unconfined)
            controller.activateScore("a")
            controller.activateScore("b")
            eventually { controller.state.value.scoreSettings?.viewMode == ViewMode.ROLL }

            assertEquals("b", controller.state.value.activeScoreId)
            assertEquals(ViewMode.ROLL, controller.state.value.scoreSettings?.viewMode)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun debouncedSaveSkipsPlaybackAndImmediateSaveWins() = runBlocking {
        val source = FakeDataSource()
        val scope = CoroutineScope(Unconfined + SupervisorJob())
        try {
            val controller = AppSettingsController(scope, source, saveDebounceMs = 50, ioDispatcher = Unconfined)
            controller.activateScore("score")
            eventually { controller.state.value.scoreSettings != null }
            val first = ScoreSettings(currentTick = 4)
            controller.scheduleScoreSave("score", first, isPlaying = true)
            delay(75)
            assertTrue(source.savedScores.value.isEmpty())

            controller.scheduleScoreSave("score", first, isPlaying = false)
            val immediate = ScoreSettings(currentTick = 8)
            controller.saveScoreNow("score", immediate)
            controller.clearActiveScore()
            eventually { source.savedScores.value.lastOrNull()?.second == immediate }

            assertEquals(listOf("score" to immediate), source.savedScores.value)
            assertNull(controller.state.value.activeScoreId)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun failedScoreSettingsLoadPublishesDiagnosticAndUsesDefaults() = runBlocking {
        val source = FakeDataSource().apply { scoreSettingsFailure = IllegalStateException("unavailable") }
        val diagnostics = DiagnosticController()
        val scope = CoroutineScope(Unconfined + SupervisorJob())
        try {
            val expected = async(start = CoroutineStart.UNDISPATCHED) { diagnostics.events.first() }
            val controller = AppSettingsController(
                scope,
                source,
                saveDebounceMs = 0,
                ioDispatcher = Unconfined,
                diagnostics = diagnostics,
            )
            controller.activateScore("score")

            val diagnostic = assertIs<AppDiagnostic.Settings>(expected.await())
            assertEquals(SettingsDiagnosticOperation.LOAD, diagnostic.operation)
            eventually { controller.state.value.scoreSettings != null }
            assertEquals("score", controller.state.value.activeScoreId)
            assertEquals(ScoreSettings(), controller.state.value.scoreSettings)
        } finally {
            scope.cancel()
        }
    }

    private suspend fun eventually(predicate: () -> Boolean) {
        repeat(100) {
            if (predicate()) return
            delay(10)
        }
        assertTrue(predicate(), "condition was not reached")
    }

    private class FakeDataSource : AppSettingsDataSource {
        var blockedScoreId: String? = null
        var scoreSettingsFailure: Throwable? = null
        var savedConfig: AppConfig? = null
        var savedLanguage: String? = null
        val savedScores = MutableStateFlow<List<Pair<String, ScoreSettings>>>(emptyList())

        override suspend fun loadConfig(): AppConfig = AppConfig(themeMode = ThemeMode.DARK)
        override suspend fun saveConfig(config: AppConfig) { savedConfig = config }
        override suspend fun loadLanguage(): String = "ja"
        override suspend fun saveLanguage(code: String) { savedLanguage = code }
        override suspend fun loadScoreSettings(scoreId: String): ScoreSettings {
            scoreSettingsFailure?.let { throw it }
            while (scoreId == blockedScoreId) delay(25)
            return ScoreSettings(viewMode = if (scoreId == "b") ViewMode.ROLL else ViewMode.SHEET_FULL)
        }
        override suspend fun saveScoreSettings(scoreId: String, settings: ScoreSettings) {
            savedScores.value = savedScores.value + (scoreId to settings)
        }
    }
}
