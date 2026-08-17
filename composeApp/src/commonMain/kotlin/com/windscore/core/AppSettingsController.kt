package com.windscore.core

import com.windscore.core.model.AppConfig
import com.windscore.core.model.ScoreSettings
import com.windscore.ui.i18n.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AppSettingsState(
    val config: AppConfig = AppConfig(),
    val language: AppLanguage = AppLanguage.CHINESE,
    val activeScoreId: String? = null,
    val scoreSettings: ScoreSettings? = null,
)

interface AppSettingsDataSource {
    suspend fun loadConfig(): AppConfig
    suspend fun saveConfig(config: AppConfig)
    suspend fun loadLanguage(): String
    suspend fun saveLanguage(code: String)
    suspend fun loadScoreSettings(scoreId: String): ScoreSettings
    suspend fun saveScoreSettings(scoreId: String, settings: ScoreSettings)
}

private object PlatformAppSettingsDataSource : AppSettingsDataSource {
    override suspend fun loadConfig(): AppConfig = com.windscore.core.loadAppConfig()
    override suspend fun saveConfig(config: AppConfig) = com.windscore.core.saveAppConfig(config)
    override suspend fun loadLanguage(): String = com.windscore.core.loadAppLanguage("zh")
    override suspend fun saveLanguage(code: String) = com.windscore.core.saveAppLanguage(code)
    override suspend fun loadScoreSettings(scoreId: String): ScoreSettings = com.windscore.core.loadScoreSettings(scoreId)
    override suspend fun saveScoreSettings(scoreId: String, settings: ScoreSettings) =
        com.windscore.core.saveScoreSettings(scoreId, settings)
}

class AppSettingsController(
    private val scope: CoroutineScope,
    private val dataSource: AppSettingsDataSource = PlatformAppSettingsDataSource,
    private val saveDebounceMs: Long = 500L,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val diagnostics: DiagnosticController? = null,
) {
    private val _state = MutableStateFlow(AppSettingsState())
    val state: StateFlow<AppSettingsState> = _state.asStateFlow()

    private var scoreLoadGeneration = 0L
    private var saveGeneration = 0L
    private var saveJob: Job? = null
    private val saveMutex = Mutex()

    init {
        scope.launch {
            val config = loadOrDefault(AppConfig()) { dataSource.loadConfig() }
            val languageCode = loadOrDefault(AppLanguage.CHINESE.code) { dataSource.loadLanguage() }
            _state.value = _state.value.copy(
                config = config,
                language = AppLanguage.fromCode(languageCode) ?: AppLanguage.CHINESE,
            )
        }
    }

    fun updateConfig(config: AppConfig) {
        _state.value = _state.value.copy(config = config)
        scope.launch(ioDispatcher) { save { dataSource.saveConfig(config) } }
    }

    fun updateLanguage(language: AppLanguage) {
        _state.value = _state.value.copy(language = language)
        scope.launch(ioDispatcher) { save { dataSource.saveLanguage(language.code) } }
    }

    fun activateScore(scoreId: String) {
        val generation = ++scoreLoadGeneration
        saveJob?.cancel()
        _state.value = _state.value.copy(activeScoreId = scoreId, scoreSettings = null)
        scope.launch {
            val settings = loadOrDefault(ScoreSettings()) { dataSource.loadScoreSettings(scoreId) }
            if (generation == scoreLoadGeneration && _state.value.activeScoreId == scoreId) {
                _state.value = _state.value.copy(scoreSettings = settings)
            }
        }
    }

    fun consumeScoreSettings() {
        _state.value = _state.value.copy(scoreSettings = null)
    }

    fun scheduleScoreSave(scoreId: String, settings: ScoreSettings, isPlaying: Boolean) {
        if (isPlaying || _state.value.activeScoreId != scoreId) return
        saveJob?.cancel()
        val generation = ++saveGeneration
        saveJob = scope.launch {
            delay(saveDebounceMs)
            withContext(ioDispatcher) {
                saveMutex.withLock {
                    if (generation == saveGeneration) save { dataSource.saveScoreSettings(scoreId, settings) }
                }
            }
        }
    }

    fun saveScoreNow(scoreId: String, settings: ScoreSettings) {
        saveJob?.cancel()
        saveGeneration++
        saveJob = null
        scope.launch(ioDispatcher) {
            saveMutex.withLock { save { dataSource.saveScoreSettings(scoreId, settings) } }
        }
    }

    fun clearActiveScore() {
        scoreLoadGeneration++
        saveJob?.cancel()
        saveJob = null
        _state.value = _state.value.copy(activeScoreId = null, scoreSettings = null)
    }

    fun close() {
        clearActiveScore()
    }

    private suspend fun <T> loadOrDefault(defaultValue: T, load: suspend () -> T): T =
        runCatching { withContext(ioDispatcher) { load() } }.getOrElse { error ->
            error.rethrowIfCancellation()
            diagnostics?.report(AppDiagnostic.Settings(SettingsDiagnosticOperation.LOAD, error))
            defaultValue
        }

    private suspend fun save(action: suspend () -> Unit) {
        runCatching { action() }.onFailure { error ->
            error.rethrowIfCancellation()
            diagnostics?.report(AppDiagnostic.Settings(SettingsDiagnosticOperation.SAVE, error))
        }
    }

    private fun Throwable.rethrowIfCancellation() {
        if (this is CancellationException) throw this
    }
}
