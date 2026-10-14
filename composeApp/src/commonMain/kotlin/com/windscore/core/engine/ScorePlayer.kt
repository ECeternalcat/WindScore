package com.windscore.core.engine

import com.windscore.audio.AudioSynth
import com.windscore.core.model.Score
import com.windscore.core.model.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 乐谱播放器（看谱模式的“播放 MIDI”）：按真实 tempo 用 [AudioSynth] 播放整谱，
 * 实时推进 [tick]（供 OSMD 光标与进度条跟随）。双声部同音重叠用引用计数，避免提前 cut off。
 */
class ScorePlayer(
    val score: Score,
    private val synth: AudioSynth,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long = kotlin.time.TimeSource.Monotonic.markNow().let { mark ->
        { mark.elapsedNow().inWholeMilliseconds }
    },
) {
    private val timing = ScoreTiming(score)

    private val _tick = MutableStateFlow(0L)
    val tick: StateFlow<Long> = _tick

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished

    private val _playingPitches = MutableStateFlow<Set<Int>>(emptySet())
    val playingPitches: StateFlow<Set<Int>> = _playingPitches

    private data class Event(val tick: Long, val on: Boolean, val pitch: Int, val onset: Long)

    private val events: List<Event> = buildList {
        score.notes.forEach { n ->
            add(Event(n.startTick, true, n.pitch, n.startTick))
            add(Event(n.startTick + n.durationTick, false, n.pitch, n.startTick))
        }
        // 同 tick 先 off 后 on
        sortWith(compareBy<Event> { it.tick }.thenBy { it.on })
    }

    private var eventIndex = 0
    private val active = mutableMapOf<Int, Int>() // pitch -> 活跃计数
    private var job: Job? = null
    private var localTick: Double = 0.0
    private var generation = 0L

    /** Playback speed multiplier (e.g. 0.5 for half speed, 1.0 for original, 1.5 for fast) */
    var speedMultiplier: Double = 1.0

    var loopRange: LongRange? = null
        set(value) = SessionLane.run {
            val normalized = playbackRegion(value).normalizedLoop()
            if (field != normalized) {
                field = normalized
                seekTo(_tick.value)
            }
        }

    /** Metronome volume (0 to 100). Default is 50. */
    var metronomeVolume: Int = 50

    fun play(): Unit = SessionLane.run {
        if (_playing.value) return@run
        if (_finished.value || _tick.value >= score.totalTicks) reset()
        synth.start()
        rebuildAt(_tick.value)
        if (loopRange?.last != _tick.value) active.keys.forEach { synth.noteOn(it, VELOCITY) }
        _playing.value = true
        _finished.value = false
        val runGeneration = ++generation
        var lastMs = nowMillis()
        var lastBeat = (_tick.value / (score.divisions.takeIf { it > 0 } ?: 4)) - 1
        job = scope.launch(Dispatchers.Default) {
            try {
                while (SessionLane.run { runGeneration == generation && _playing.value && localTick < score.totalTicks }) {
                    delay(LOOP_MS)
                    var wrap: LongRange? = null
                    SessionLane.run {
                        if (runGeneration != generation || !_playing.value) return@run
                        val now = nowMillis()
                        var elapsed = (now - lastMs).coerceAtLeast(0).toDouble()
                        if (elapsed > 100) elapsed = 50.0 // Retain the existing anti-burst lag policy.
                        lastMs = now
                        localTick = timing.tickAtMilliseconds(timing.millisecondsAt(localTick) + elapsed * speedMultiplier)
                        val tickLong = (localTick + 1e-9).toLong()
                        val divisions = score.divisions.takeIf { it > 0 } ?: 4
                        val beat = tickLong / divisions
                        if (beat > lastBeat && tickLong < playbackRegion().end) {
                            lastBeat = beat
                            if (metronomeVolume > 0) {
                                val velocity = (metronomeVolume * 1.27).toInt().coerceIn(0, 127)
                                scope.launch(Dispatchers.Default) {
                                    synth.noteOn(9, if (beat % 4L == 0L) 76 else 77, velocity)
                                    delay(60)
                                    synth.noteOff(9, if (beat % 4L == 0L) 76 else 77)
                                }
                            }
                        }
                        val region = playbackRegion()
                        val limit = minOf(tickLong, region.end)
                        while (eventIndex < events.size && events[eventIndex].tick <= limit) {
                            val event = events[eventIndex++]
                            if (region.containsNoteAt(event.onset) && (!event.on || event.tick < region.end)) applyEvent(event)
                        }
                        val currentLoop = loopRange
                        if (currentLoop != null && tickLong >= currentLoop.last) {
                            _tick.value = currentLoop.last
                            silenceAll()
                            _playingPitches.value = emptySet()
                            wrap = currentLoop
                        } else {
                            _tick.value = tickLong.coerceAtMost(score.totalTicks)
                            _playingPitches.value = active.keys.toSet()
                        }
                    }
                    wrap?.let { range ->
                        delay(500)
                        SessionLane.run {
                            if (runGeneration != generation) return@run
                            localTick = range.first.toDouble()
                            _tick.value = range.first
                            rebuildAt(range.first)
                            lastBeat = range.first / (score.divisions.takeIf { it > 0 } ?: 4) - 1
                        }
                        delay(500)
                        // Presentation pauses never become elapsed musical time in the next pass.
                        SessionLane.run { if (runGeneration == generation) lastMs = nowMillis() }
                    }
                }
                SessionLane.run {
                    if (runGeneration == generation && localTick >= score.totalTicks) {
                        _tick.value = score.totalTicks
                        _finished.value = true
                        silenceAll()
                        _playingPitches.value = emptySet()
                    }
                }
            } finally {
                SessionLane.run { if (runGeneration == generation) _playing.value = false }
            }
        }
    }

    fun pause(): Unit = SessionLane.run {
        generation++
        _playing.value = false
        job?.cancel()
        silenceAll()
        _playingPitches.value = emptySet()
    }

    fun stop(): Unit = SessionLane.run {
        pause()
        reset()
    }

    fun reset(): Unit = SessionLane.run {
        val wasPlaying = _playing.value
        pause()
        val target = playbackRegion().start
        localTick = target.toDouble()
        _tick.value = target
        rebuildAt(target)
        _finished.value = false
        if (wasPlaying) play()
    }

    fun toggle() {
        if (_playing.value) pause() else play()
    }

    /** 定点跳转（文档 3.3）。静默重建状态——不触发合成器，避免跳转杂音。 */
    fun seekTo(tick: Long): Unit = SessionLane.run {
        val wasPlaying = _playing.value
        pause()
        val target = playbackRegion().constrain(tick)
        localTick = target.toDouble()
        _tick.value = target
        rebuildAt(target) // Silent until play; retain exact-target attacks for dispatch.
        _finished.value = target >= score.totalTicks
        if (wasPlaying) play()
    }

    private fun rebuildAt(target: Long) {
        eventIndex = 0
        active.clear()
        val region = playbackRegion()
        while (eventIndex < events.size && events[eventIndex].tick < target) {
            val event = events[eventIndex++]
            if (!region.containsNoteAt(event.onset)) continue
            val count = (active[event.pitch] ?: 0) + if (event.on) 1 else -1
            if (count <= 0) active.remove(event.pitch) else active[event.pitch] = count
        }
    }

    /** 微观步进：跳到下一个音符的起始 tick（文档 3.3 盲操微调）。 */
    fun stepForward() {
        val current = _tick.value
        val region = playbackRegion()
        val nextTick = score.notes.firstOrNull {
            it.startTick > current && region.containsNoteAt(it.startTick)
        }?.startTick ?: region.end
        seekTo(nextTick)
    }

    /** 微观步进：跳到上一个音符的起始 tick（文档 3.3 盲操微调）。 */
    fun stepBackward() {
        val current = _tick.value
        val region = playbackRegion()
        val prevTick = score.notes.lastOrNull {
            it.startTick < current && region.containsNoteAt(it.startTick)
        }?.startTick ?: region.start
        seekTo(prevTick)
    }

    private fun playbackRegion(range: LongRange? = loopRange): PlaybackRegion = PlaybackRegion(
        scoreStart = 0L,
        scoreEnd = score.totalTicks,
        loopStart = range?.first,
        loopEnd = range?.last,
    )

    private fun applyEvent(e: Event) {
        if (e.on) {
            active[e.pitch] = (active[e.pitch] ?: 0) + 1
            synth.noteOn(e.pitch, VELOCITY)
        } else {
            val count = active[e.pitch] ?: return
            val c = count - 1
            if (c <= 0) {
                active.remove(e.pitch)
                synth.noteOff(e.pitch)
            } else {
                active[e.pitch] = c
            }
        }
    }

    private fun silenceAll() {
        active.keys.toList().forEach { synth.noteOff(it) }
        active.clear()
    }

    private companion object {
        const val LOOP_MS = 10L
        const val VELOCITY = 90
    }
}
