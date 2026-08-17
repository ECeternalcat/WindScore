package com.windscore.core

import com.windscore.core.model.Score
import com.windscore.core.model.ScoreRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class OpenedScore(
    val score: Score,
    val history: List<ScoreRecord>,
)

interface ScoreLibraryDataSource {
    suspend fun loadRecentScores(): List<Score>
    suspend fun loadScoreXml(scoreId: String): String?
    suspend fun loadScoreHistory(scoreId: String): List<ScoreRecord>
    suspend fun saveRecentScores(scores: List<Score>)
    suspend fun appendScoreRecord(scoreId: String, record: ScoreRecord): List<ScoreRecord>
}

private object PlatformScoreLibraryDataSource : ScoreLibraryDataSource {
    override suspend fun loadRecentScores(): List<Score> = loadRecentScoresBlocking()
    override suspend fun loadScoreXml(scoreId: String): String? = com.windscore.core.loadScoreXml(scoreId)
    override suspend fun loadScoreHistory(scoreId: String): List<ScoreRecord> = com.windscore.core.loadScoreHistory(scoreId)
    override suspend fun saveRecentScores(scores: List<Score>) = com.windscore.core.saveRecentScores(scores)
    override suspend fun appendScoreRecord(scoreId: String, record: ScoreRecord): List<ScoreRecord> =
        com.windscore.core.appendScoreRecord(scoreId, record)

    private suspend fun loadRecentScoresBlocking(): List<Score> {
        var loaded = emptyList<Score>()
        com.windscore.core.loadRecentScores { loaded = it }
        return loaded
    }
}

class ScoreLibraryController(
    private val scope: CoroutineScope,
    private val dataSource: ScoreLibraryDataSource = PlatformScoreLibraryDataSource,
    private val diagnostics: DiagnosticController? = null,
) {
    private val _recentScores = MutableStateFlow<List<Score>>(emptyList())
    val recentScores: StateFlow<List<Score>> = _recentScores.asStateFlow()

    private val _openedScore = MutableStateFlow<OpenedScore?>(null)
    val openedScore: StateFlow<OpenedScore?> = _openedScore.asStateFlow()

    private val _scoreHistory = MutableStateFlow<List<ScoreRecord>>(emptyList())
    val scoreHistory: StateFlow<List<ScoreRecord>> = _scoreHistory.asStateFlow()

    private val _opening = MutableStateFlow(false)
    val opening: StateFlow<Boolean> = _opening.asStateFlow()

    private val _error = MutableStateFlow<Throwable?>(null)
    val error: StateFlow<Throwable?> = _error.asStateFlow()

    private var generation = 0L
    private var mutationGeneration = 0L
    private var persistGeneration = 0L
    private var loadJob: Job? = null
    private var activeScoreId: String? = null
    private val persistMutex = Mutex()

    init {
        val initialGeneration = mutationGeneration
        scope.launch(Dispatchers.IO) {
            runCatching { dataSource.loadRecentScores() }
                .onSuccess { loaded ->
                    if (initialGeneration == mutationGeneration) _recentScores.value = loaded
                }
                .onFailure { error ->
                    error.rethrowIfCancellation()
                    diagnostics?.report(AppDiagnostic.Library(LibraryDiagnosticOperation.LOAD, error))
                }
        }
    }

    fun open(score: Score) {
        val request = ++generation
        loadJob?.cancel()
        loadJob = scope.launch {
            _opening.value = true
            _error.value = null
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val scoreId = score.id.takeIf { it.isNotEmpty() }
                        ?: score.rawXml?.let(::calculateMd5)
                        ?: ""
                    val xml = score.rawXml ?: scoreId.takeIf { it.isNotEmpty() }?.let { dataSource.loadScoreXml(it) }
                    check(!score.supportsSheetView || xml != null) {
                        "MusicXML source is unavailable for score $scoreId"
                    }
                    val finalScore = score.copy(
                        id = scoreId,
                        rawXml = xml,
                        supportsSheetView = xml != null,
                    )
                    OpenedScore(
                        score = finalScore,
                        history = dataSource.loadScoreHistory(scoreId),
                    )
                }
            }
            if (request != generation) return@launch
            _opening.value = false
            result.onSuccess { opened ->
                updateRecentScores(opened.score)
                activeScoreId = opened.score.id
                _scoreHistory.value = opened.history
                _openedScore.value = opened
            }.onFailure { error ->
                error.rethrowIfCancellation()
                _error.value = error
                diagnostics?.report(AppDiagnostic.Library(LibraryDiagnosticOperation.OPEN, error))
            }
        }
    }

    fun consumeOpenedScore() {
        _openedScore.value = null
    }

    fun cancelOpening() {
        generation++
        loadJob?.cancel()
        loadJob = null
        _opening.value = false
    }

    fun rename(score: Score, title: String) {
        mutationGeneration++
        val updated = _recentScores.value.map {
            if (sameScoreIdentity(it, score)) it.copy(title = title) else it
        }
        _recentScores.value = updated
        persist(updated)
    }

    fun delete(score: Score) {
        mutationGeneration++
        val updated = _recentScores.value.filterNot { sameScoreIdentity(it, score) }
        _recentScores.value = updated
        persist(updated)
    }

    fun appendHistory(scoreId: String, record: ScoreRecord) {
        scope.launch {
            val history = runCatching {
                withContext(Dispatchers.IO) { dataSource.appendScoreRecord(scoreId, record) }
            }.getOrElse { error ->
                error.rethrowIfCancellation()
                diagnostics?.report(AppDiagnostic.Library(LibraryDiagnosticOperation.HISTORY, error))
                return@launch
            }
            val opened = _openedScore.value
            if (activeScoreId == scoreId) {
                _scoreHistory.value = history
                if (opened?.score?.id == scoreId) {
                    _openedScore.value = opened.copy(history = history)
                }
            }
        }
    }

    fun clearActiveScore() {
        activeScoreId = null
        _scoreHistory.value = emptyList()
    }

    fun close() {
        cancelOpening()
    }

    private fun updateRecentScores(score: Score) {
        mutationGeneration++
        val entry = score.copy(rawXml = null)
        val updated = buildList {
            add(entry)
            _recentScores.value.filterNot { sameScoreIdentity(it, entry) }
                .take(9)
                .forEach(::add)
        }
        _recentScores.value = updated
        persist(updated.map { if (sameScoreIdentity(it, entry)) score else it })
    }

    private fun persist(scores: List<Score>) {
        val generation = ++persistGeneration
        scope.launch(Dispatchers.IO) {
            persistMutex.withLock {
                if (generation == persistGeneration) {
                    runCatching { dataSource.saveRecentScores(scores) }
                        .onFailure { error ->
                            error.rethrowIfCancellation()
                            diagnostics?.report(AppDiagnostic.Library(LibraryDiagnosticOperation.SAVE, error))
                        }
                }
            }
        }
    }

    private fun sameScoreIdentity(left: Score, right: Score): Boolean =
        if (left.id.isNotBlank() && right.id.isNotBlank()) left.id == right.id
        else left.title == right.title && left.totalTicks == right.totalTicks

    private fun Throwable.rethrowIfCancellation() {
        if (this is CancellationException) throw this
    }
}
