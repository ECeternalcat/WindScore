package com.windscore.core.engine

import com.windscore.audio.AudioSynth
import com.windscore.core.model.PracticeMode
import com.windscore.core.model.Score
import com.windscore.core.model.ScoreRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ScoringPhase { SETUP, ACTIVE, RESULT }

data class ScoringState(
    val phase: ScoringPhase = ScoringPhase.SETUP,
    val session: ScoreSession? = null,
)

class ScoringController(
    private val synth: AudioSynth,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val onRecord: (String, ScoreRecord) -> Unit,
    private val resultDelayMs: Long = 1_000L,
) {
    private val _state = MutableStateFlow(ScoringState())
    val state: StateFlow<ScoringState> = _state.asStateFlow()

    private var completionJob: Job? = null

    fun start(score: Score, mode: PracticeMode): ScoreSession {
        closeSession()
        val session = ScoreSession(score, synth, scope, mode)
        _state.value = ScoringState(ScoringPhase.ACTIVE, session)
        completionJob = scope.launch {
            while (!session.finished) delay(COMPLETION_POLL_MS)
            delay(resultDelayMs)
            if (_state.value.session !== session || _state.value.phase != ScoringPhase.ACTIVE) return@launch
            onRecord(
                score.id,
                ScoreRecord(
                    timestamp = now(),
                    percent = session.scorePercent(),
                    cleanHits = session.cleanHitCount(),
                    totalNotes = session.totalActiveNotes(),
                    wrongPresses = session.wrongPressCount(),
                    practiceMode = session.practiceMode,
                ),
            )
            _state.value = ScoringState(ScoringPhase.RESULT, session)
        }
        return session
    }

    fun onNoteOn(pitch: Int): Boolean {
        val session = _state.value.session ?: return false
        session.onNoteOn(pitch)
        return true
    }

    fun onNoteOff(pitch: Int): Boolean {
        val session = _state.value.session ?: return false
        session.onNoteOff(pitch)
        return true
    }

    fun exit() {
        closeSession()
        _state.value = ScoringState()
    }

    fun close() = exit()

    private fun closeSession() {
        completionJob?.cancel()
        completionJob = null
        _state.value.session?.close()
    }

    private companion object {
        const val COMPLETION_POLL_MS = 20L
    }
}
