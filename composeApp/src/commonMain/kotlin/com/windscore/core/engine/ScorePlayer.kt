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
) {
    private val tps: Double = run {
        val tempo = score.tempo.takeIf { it > 0 } ?: 120
        val div = score.divisions.takeIf { it > 0 } ?: 4
        tempo * div / 60.0
    }

    private val _tick = MutableStateFlow(0L)
    val tick: StateFlow<Long> = _tick

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished

    private val _playingPitches = MutableStateFlow<Set<Int>>(emptySet())
    val playingPitches: StateFlow<Set<Int>> = _playingPitches

    private data class Event(val tick: Long, val on: Boolean, val pitch: Int)

    private val events: List<Event> = buildList {
        score.notes.forEach { n ->
            add(Event(n.startTick, true, n.pitch))
            add(Event(n.startTick + n.durationTick, false, n.pitch))
        }
        // 同 tick 先 off 后 on
        sortBy { it.tick * 2L + (if (it.on) 1L else 0L) }
    }

    private var eventIndex = 0
    private val active = mutableMapOf<Int, Int>() // pitch -> 活跃计数
    private var job: Job? = null
    private var localTick: Double = 0.0

    /** Playback speed multiplier (e.g. 0.5 for half speed, 1.0 for original, 1.5 for fast) */
    var speedMultiplier: Double = 1.0

    var loopRange: LongRange? = null
        set(value) {
            val normalized = playbackRegion(value).normalizedLoop()
            if (field == normalized) return
            field = normalized
            seekTo(_tick.value)
        }

    /** Metronome volume (0 to 100). Default is 50. */
    var metronomeVolume: Int = 50

    fun play() {
        if (_playing.value) return
        if (_finished.value || _tick.value >= score.totalTicks) reset()
        synth.start()
        _playing.value = true
        _finished.value = false
        job = scope.launch(Dispatchers.Default) {
            val clock = kotlin.time.TimeSource.Monotonic
            val startMark = clock.markNow()
            var lastMs = startMark.elapsedNow().inWholeMilliseconds
            localTick = _tick.value.toDouble()
            var lastBeat = (localTick.toLong() / (score.divisions.takeIf { it > 0 } ?: 4)) - 1
            try {
                while (isActive && _playing.value && localTick < score.totalTicks) {
                    delay(LOOP_MS)
                    val now = startMark.elapsedNow().inWholeMilliseconds
                    var dt = (now - lastMs) / 1000.0
                    if (dt > 0.1) {
                        dt = 0.05 // Cap to 50ms to prevent note blasting/skipping during system lags
                    }
                    lastMs = now
                    localTick += tps * dt * speedMultiplier
                    val tickLong = localTick.toLong()

                    val divisions = score.divisions.takeIf { it > 0 } ?: 4
                    val currentBeat = tickLong / divisions
                    if (currentBeat > lastBeat) {
                        lastBeat = currentBeat
                        if (metronomeVolume > 0) {
                            val vel = (metronomeVolume * 1.27).toInt().coerceIn(0, 127)
                            // 使用打击乐通道（GM channel 10 / 索引 9）上的木块音色，
                            // 避免与乐谱钢琴音域（96/108 等真实音高）冲突、互相切断
                            val clickPitch = if (currentBeat % 4L == 0L) 76 else 77 // High / Low Woodblock
                            val clickChannel = 9
                            scope.launch(Dispatchers.Default) {
                                synth.noteOn(clickChannel, clickPitch, vel)
                                delay(60)
                                synth.noteOff(clickChannel, clickPitch)
                            }
                        }
                    }

                    val currentLoop = loopRange
                    val eventLimit = minOf(tickLong, currentLoop?.last ?: score.totalTicks)
                    while (eventIndex < events.size && events[eventIndex].tick <= eventLimit) {
                        val event = events[eventIndex]
                        if (!event.on || currentLoop == null || (event.tick >= currentLoop.first && event.tick < currentLoop.last)) {
                            applyEvent(event)
                        }
                        eventIndex++
                    }

                    if (currentLoop != null && tickLong >= currentLoop.last) {
                        // 1. Wait at the end of the loop so user can see the last notes
                        _tick.value = currentLoop.last
                        silenceAll()
                        _playingPitches.value = emptySet()
                        delay(500)

                        // 2. Snap back to the beginning of the loop
                        localTick = currentLoop.first.toDouble()
                        _tick.value = currentLoop.first
                        eventIndex = 0
                        silenceAll()
                        while (eventIndex < events.size && events[eventIndex].tick < currentLoop.first) {
                            val e = events[eventIndex]
                            if (e.on) {
                                active[e.pitch] = (active[e.pitch] ?: 0) + 1
                            } else {
                                val c = (active[e.pitch] ?: 0) - 1
                                if (c <= 0) active.remove(e.pitch) else active[e.pitch] = c
                            }
                            eventIndex++
                        }
                        // 3. Wait at the start of the loop before playing
                        delay(500)
                        lastMs = startMark.elapsedNow().inWholeMilliseconds
                    } else {
                        _tick.value = tickLong.coerceAtMost(score.totalTicks)
                    }
                    _playingPitches.value = active.keys.toSet()
                }
                if (localTick >= score.totalTicks) {
                    _tick.value = score.totalTicks
                    _finished.value = true
                    _playing.value = false
                    silenceAll()
                }
            } finally {
                _playing.value = false
            }
        }
    }

    fun pause() {
        _playing.value = false
        job?.cancel()
        silenceAll()
        _playingPitches.value = emptySet()
    }

    fun stop() {
        pause()
        reset()
    }

    fun reset() {
        val target = playbackRegion().start
        _tick.value = target
        localTick = target.toDouble()
        eventIndex = 0
        active.clear()
        // Rebuild notes already sounding at the loop start without triggering audio.
        while (eventIndex < events.size && events[eventIndex].tick < target) {
            val event = events[eventIndex]
            if (event.on) {
                active[event.pitch] = (active[event.pitch] ?: 0) + 1
            } else {
                val count = (active[event.pitch] ?: 0) - 1
                if (count <= 0) active.remove(event.pitch) else active[event.pitch] = count
            }
            eventIndex++
        }
        _finished.value = false
        _playingPitches.value = emptySet()
    }

    fun toggle() {
        if (_playing.value) pause() else play()
    }

    /** 定点跳转（文档 3.3）。静默重建状态——不触发合成器，避免跳转杂音。 */
    fun seekTo(tick: Long) {
        val wasPlaying = _playing.value
        _playing.value = false
        job?.cancel()
        silenceAll()
        val target = playbackRegion().constrain(tick)
        localTick = target.toDouble()
        _tick.value = target
        eventIndex = 0
        active.clear()
        // 静默推进 eventIndex + 重建 active 映射（不调用 synth，不发声）
        // 严格 < target：目标位置的音符留给 play 循环触发，避免吞掉第一个音
        while (eventIndex < events.size && events[eventIndex].tick < target) {
            val e = events[eventIndex]
            if (e.on) {
                active[e.pitch] = (active[e.pitch] ?: 0) + 1
            } else {
                val c = (active[e.pitch] ?: 0) - 1
                if (c <= 0) active.remove(e.pitch) else active[e.pitch] = c
            }
            eventIndex++
        }
        _playingPitches.value = emptySet()
        _finished.value = target >= score.totalTicks
        if (wasPlaying) play()
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
