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

    fun play() {
        if (_playing.value) return
        if (_finished.value || _tick.value >= score.totalTicks) reset()
        synth.start()
        _playing.value = true
        _finished.value = false
        job = scope.launch(Dispatchers.Default) {
            var lastMs = System.currentTimeMillis()
            localTick = _tick.value.toDouble()
            try {
                while (isActive && _playing.value && localTick < score.totalTicks) {
                    delay(LOOP_MS)
                    val now = System.currentTimeMillis()
                    val dt = (now - lastMs) / 1000.0
                    lastMs = now
                    localTick += tps * dt
                    val tickLong = localTick.toLong()
                    while (eventIndex < events.size && events[eventIndex].tick <= tickLong) {
                        applyEvent(events[eventIndex])
                        eventIndex++
                    }
                    _tick.value = tickLong.coerceAtMost(score.totalTicks)
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
        _tick.value = 0L
        eventIndex = 0
        active.clear()
        _finished.value = false
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
        val target = tick.coerceIn(0L, score.totalTicks)
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
        val nextTick = score.notes.firstOrNull { it.startTick > current }?.startTick ?: score.totalTicks
        seekTo(nextTick)
    }

    /** 微观步进：跳到上一个音符的起始 tick（文档 3.3 盲操微调）。 */
    fun stepBackward() {
        val current = _tick.value
        val prevTick = score.notes.lastOrNull { it.startTick < current }?.startTick ?: 0L
        seekTo(prevTick)
    }

    private fun applyEvent(e: Event) {
        if (e.on) {
            active[e.pitch] = (active[e.pitch] ?: 0) + 1
            synth.noteOn(e.pitch, VELOCITY)
        } else {
            val c = (active[e.pitch] ?: 0) - 1
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
