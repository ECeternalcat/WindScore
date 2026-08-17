package com.windscore.core.engine

import com.windscore.audio.AudioSynth
import com.windscore.core.MidiNoteHandler
import com.windscore.core.model.PracticeMode
import com.windscore.core.model.Score
import com.windscore.core.traceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PracticeState(
    val score: Score? = null,
    val session: ScoreSession? = null,
    val mode: PracticeMode = PracticeMode.BOTH,
)

class PracticeController(
    private val synth: AudioSynth,
    private val scope: CoroutineScope,
) : MidiNoteHandler {
    private val _state = MutableStateFlow(PracticeState())
    val state: StateFlow<PracticeState> = _state.asStateFlow()

    fun load(
        score: Score,
        mode: PracticeMode,
        currentTick: Long = 0L,
        loopRange: LongRange? = null,
    ): ScoreSession {
        traceState(
            "practice.load score=${score.id} mode=$mode tick=$currentTick loop=$loopRange " +
                "oldSession=${_state.value.session?.traceId}",
        )
        val newSession = ScoreSession(score, synth, scope, mode).apply {
            if (loopRange != null) {
                setLoop(loopRange.first, loopRange.last, playAudio = false)
            }
            seekTo(currentTick)
        }
        val oldSession = _state.value.session
        _state.value = PracticeState(score, newSession, mode)
        traceState("practice.load published session=${newSession.traceId} tick=${newSession.currentTick}")
        oldSession?.close()
        return newSession
    }

    fun changeMode(mode: PracticeMode): ScoreSession? {
        val current = _state.value
        val score = current.score ?: return null
        if (current.mode == mode) return current.session
        traceState("practice.changeMode ${current.mode}->$mode session=${current.session?.traceId}")
        val session = current.session
        return load(
            score = score,
            mode = mode,
            currentTick = session?.currentTick ?: 0L,
            loopRange = session?.takeIf { it.isLoopActive }?.loopRange,
        )
    }

    /** Terminal fallback handler: always consumes so events never fall through. */
    override fun onNoteOn(pitch: Int): Boolean {
        _state.value.session?.onNoteOn(pitch)
        return true
    }

    override fun onNoteOff(pitch: Int): Boolean {
        _state.value.session?.onNoteOff(pitch)
        return true
    }

    fun close() {
        _state.value.session?.close()
        _state.value = PracticeState()
    }
}
