package com.windscore.core.engine

import com.windscore.audio.AudioSynth
import com.windscore.core.model.Score
import com.windscore.core.traceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaybackState(
    val tick: Long = 0L,
    val isPlaying: Boolean = false,
    val playingPitches: Set<Int> = emptySet(),
    val speed: Float = 1f,
    val metronomeVolume: Int = 50,
    val loopRange: LongRange? = null,
)

class PlaybackController(
    private val synth: AudioSynth,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var player: ScorePlayer? = null
    private var session: ScoreSession? = null
    private var timelineJob: Job? = null
    private var pitchesJob: Job? = null
    private var practiceSyncEnabled = true

    fun load(
        score: Score,
        session: ScoreSession,
        initialTick: Long = session.currentTick,
        loopRange: LongRange? = session.takeIf { it.isLoopActive }?.loopRange,
        speed: Float = 1f,
        metronomeVolume: Int = 50,
    ) {
        releasePlayer()
        this.session = session
        val newPlayer = ScorePlayer(score, synth, scope).apply {
            speedMultiplier = speed.toDouble()
            this.metronomeVolume = metronomeVolume
            this.loopRange = loopRange
            seekTo(initialTick)
        }
        player = newPlayer
        _state.value = PlaybackState(
            tick = newPlayer.tick.value,
            speed = speed,
            metronomeVolume = metronomeVolume,
            loopRange = newPlayer.loopRange,
        )
        scope.launch(Dispatchers.Default) { synth.start() }
        timelineJob = scope.launch {
            kotlinx.coroutines.flow.combine(newPlayer.tick, newPlayer.playing) { tick, playing -> tick to playing }
                .collect { (tick, playing) ->
                    if (tick != newPlayer.tick.value || playing != newPlayer.playing.value) return@collect
                    _state.update { it.copy(tick = tick, isPlaying = playing) }
                    syncSession(tick, playing)
                }
        }
        pitchesJob = scope.launch {
            newPlayer.playingPitches.collect { pitches ->
                _state.update { it.copy(playingPitches = pitches) }
            }
        }
    }

    fun attachSession(session: ScoreSession) {
        traceState("playback.attachSession session=${session.traceId} tick=${_state.value.tick} playing=${_state.value.isPlaying}")
        this.session = session
        syncSession(_state.value.tick, _state.value.isPlaying)
    }

    fun setPracticeSyncEnabled(enabled: Boolean) {
        practiceSyncEnabled = enabled
        if (enabled) syncSession(_state.value.tick, _state.value.isPlaying)
    }

    fun setSpeed(speed: Float) {
        val normalized = speed.coerceIn(0f, 2f)
        player?.speedMultiplier = normalized.toDouble()
        _state.update { it.copy(speed = normalized) }
    }

    fun setMetronomeVolume(volume: Int) {
        val normalized = volume.coerceIn(0, 100)
        player?.metronomeVolume = normalized
        _state.update { it.copy(metronomeVolume = normalized) }
    }

    fun setLoop(start: Long, end: Long) {
        val range = if (start <= end) start..end else end..start
        session?.setLoop(range.first, range.last)
        player?.loopRange = range
        player?.seekTo(range.first)
        publishTick()
        _state.update { it.copy(loopRange = player?.loopRange) }
    }

    fun clearLoop() {
        session?.clearLoop()
        player?.loopRange = null
        val target = session?.currentTick ?: 0L
        player?.seekTo(target)
        publishTick()
        _state.update { it.copy(loopRange = null) }
    }

    fun seekTo(tick: Long) {
        player?.seekTo(tick)
        val target = player?.tick?.value ?: tick
        session?.seekTo(target)
        publishTick()
    }

    fun stepForward() {
        player?.stepForward()
        seekSessionToPlayer()
        publishTick()
    }

    fun stepBackward() {
        player?.stepBackward()
        seekSessionToPlayer()
        publishTick()
    }

    fun toggle() {
        val currentPlayer = player ?: return
        if (!currentPlayer.playing.value && currentPlayer.tick.value == 0L) {
            currentPlayer.seekTo(session?.currentTick ?: 0L)
        }
        currentPlayer.toggle()
    }

    fun stop() {
        player?.stop()
        seekSessionToPlayer()
        publishTick()
    }

    fun reset() {
        traceState("playback.reset session=${session?.traceId} playerTick=${player?.tick?.value}")
        player?.reset()
        session?.reset()
        seekSessionToPlayer()
        publishTick()
    }

    fun close() {
        releasePlayer()
        session = null
        practiceSyncEnabled = true
        _state.value = PlaybackState(
            speed = _state.value.speed,
            metronomeVolume = _state.value.metronomeVolume,
        )
    }

    private fun seekSessionToPlayer() {
        val currentPlayer = player ?: return
        val tick = currentPlayer.tick.value
        session?.seekTo(tick)
    }

    /** Position commands are synchronous; publish tick immediately instead of waiting for the async collector. */
    private fun publishTick() {
        player?.let { p -> _state.update { it.copy(tick = p.tick.value) } }
    }

    private fun syncSession(tick: Long, playing: Boolean) {
        if (!practiceSyncEnabled) return
        session?.let {
            traceState(
                "playback.syncSession session=${it.traceId} sessionTick=${it.currentTick} " +
                    "playerTick=$tick playing=$playing",
            )
            it.setHostedEnabled(!playing)
            it.seekTo(tick)
        }
    }

    private fun releasePlayer() {
        timelineJob?.cancel()
        pitchesJob?.cancel()
        timelineJob = null
        pitchesJob = null
        player?.stop()
        player = null
    }
}
