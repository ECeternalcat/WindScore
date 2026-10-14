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
    private var playbackActive = false

    /** true is sent synchronously BEFORE playback starts; false after position normalization. */
    var onPlaybackActiveChanged: ((Boolean) -> Unit)? = null

    private fun notifyPlayback(active: Boolean) {
        if (playbackActive == active) return
        playbackActive = active
        runCatching { onPlaybackActiveChanged?.invoke(active) }
    }

    fun load(
        score: Score,
        session: ScoreSession,
        initialTick: Long = session.currentTick,
        loopRange: LongRange? = session.takeIf { it.isLoopActive }?.loopRange,
        speed: Float = 1f,
        metronomeVolume: Int = 50,
    ) = SessionLane.run {
        releasePlayer()
        this.session = session
        val newPlayer = ScorePlayer(score, synth, scope).apply {
            speedMultiplier = speed.toDouble()
            this.metronomeVolume = metronomeVolume
            this.loopRange = loopRange
            seekTo(initialTick)
        }
        player = newPlayer
        session.setPracticeSpeed(speed)
        session.setHostedEnabled(true)
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
                    SessionLane.run {
                        // Validation AND mutation share the command lane: stale collector emissions cannot seek back.
                        if (player !== newPlayer || tick != newPlayer.tick.value || playing != newPlayer.playing.value) return@run
                        val wasPlaying = _state.value.isPlaying
                        _state.update { it.copy(tick = tick, isPlaying = playing) }
                        if (playing || wasPlaying || playbackActive) syncSession(tick, playing)
                        notifyPlayback(playing)
                    }
                }
        }
        pitchesJob = scope.launch {
            newPlayer.playingPitches.collect { pitches ->
                _state.update { it.copy(playingPitches = pitches) }
            }
        }
    }

    fun attachSession(session: ScoreSession) = SessionLane.run {
        traceState("playback.attachSession session=${session.traceId} tick=${_state.value.tick} playing=${_state.value.isPlaying}")
        this.session = session
        session.setPracticeSpeed(_state.value.speed)
        session.setHostedEnabled(!_state.value.isPlaying)
        if (_state.value.isPlaying) syncSession(_state.value.tick, true)
    }

    fun setPracticeSyncEnabled(enabled: Boolean) = SessionLane.run {
        practiceSyncEnabled = enabled
        if (enabled) session?.setHostedEnabled(!playbackActive)
    }

    fun setSpeed(speed: Float) = SessionLane.run {
        val normalized = speed.coerceIn(0f, 2f)
        player?.speedMultiplier = normalized.toDouble()
        session?.setPracticeSpeed(normalized)
        _state.update { it.copy(speed = normalized) }
    }

    fun setMetronomeVolume(volume: Int) {
        val normalized = volume.coerceIn(0, 100)
        player?.metronomeVolume = normalized
        _state.update { it.copy(metronomeVolume = normalized) }
    }

    fun setLoop(start: Long, end: Long) = SessionLane.run {
        val range = if (start <= end) start..end else end..start
        session?.setLoop(range.first, range.last)
        player?.loopRange = range
        player?.seekTo(range.first)
        publishTick()
        _state.update { it.copy(loopRange = player?.loopRange) }
    }

    fun clearLoop() = SessionLane.run {
        session?.clearLoop()
        player?.loopRange = null
        val target = session?.currentTick ?: 0L
        player?.seekTo(target)
        publishTick()
        _state.update { it.copy(loopRange = null) }
    }

    fun seekTo(tick: Long) = SessionLane.run {
        player?.seekTo(tick)
        val target = player?.tick?.value ?: tick
        session?.seekTo(target)
        publishTick()
    }

    fun stepForward() = SessionLane.run {
        player?.stepForward()
        seekSessionToPlayer()
        publishTick()
    }

    fun stepBackward() = SessionLane.run {
        player?.stepBackward()
        seekSessionToPlayer()
        publishTick()
    }

    fun toggle() = SessionLane.run {
        val currentPlayer = player ?: return@run
        if (!currentPlayer.playing.value && currentPlayer.tick.value == 0L) {
            currentPlayer.seekTo(session?.currentTick ?: 0L)
        }
        val starting = !currentPlayer.playing.value
        if (starting) {
            notifyPlayback(true)
            session?.setHostedEnabled(false)
        }
        currentPlayer.toggle()
        val playing = currentPlayer.playing.value
        _state.update { it.copy(isPlaying = playing, tick = currentPlayer.tick.value) }
        syncSession(currentPlayer.tick.value, playing)
        notifyPlayback(playing)
    }

    fun stop() = SessionLane.run {
        player?.stop()
        seekSessionToPlayer()
        publishTick()
        session?.setHostedEnabled(true)
        _state.update { it.copy(isPlaying = false) }
        notifyPlayback(false)
    }

    fun reset() = SessionLane.run {
        traceState("playback.reset session=${session?.traceId} playerTick=${player?.tick?.value}")
        player?.reset()
        session?.reset()
        seekSessionToPlayer()
        publishTick()
    }

    fun close() = SessionLane.run {
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
        playbackActive = false
    }
}
