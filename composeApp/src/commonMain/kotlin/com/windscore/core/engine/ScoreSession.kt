package com.windscore.core.engine

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.windscore.audio.AudioSynth
import com.windscore.core.model.NoteEntity
import com.windscore.core.model.PracticeMode
import com.windscore.core.model.Score
import com.windscore.core.model.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * [JudgmentEngine] 的 Compose 友好包装，并负责**托管轨道的自动发声**（文档 4.2）：
 * 游标每落到新组，由 [AudioSynth] 主动 noteOn 托管音，并按音符持续时长延迟 noteOff。
 *
 * 额外责任：对连续纯托管段按真实节奏定时推进，避免瞬时 burst。
 */
class ScoreSession(
    val score: Score,
    private val synth: AudioSynth,
    private val scope: CoroutineScope,
    practiceMode: PracticeMode = PracticeMode.BOTH,
) {
    private val engine = JudgmentEngine(score, practiceMode)

    private val msPerTick: Double = run {
        val tempo = score.tempo.takeIf { it > 0 } ?: 120
        val div = score.divisions.takeIf { it > 0 } ?: 4
        60_000.0 / (tempo * div)
    }

    /** 托管段自动推进任务；遇到练习组或结束时取消。 */
    private var hostedJob: Job? = null

    /** 上一个已处理托管组的起始 tick，用于计算下一托管组的节奏间隔。 */
    private var lastHostedTick: Long = -1

    private val _currentTick: MutableState<Long> = mutableStateOf(engine.currentTick)
    var currentTick: Long
        get() = _currentTick.value
        private set(value) { _currentTick.value = value }

    private val _progress: MutableState<Int> = mutableStateOf(engine.progress)
    var progress: Int
        get() = _progress.value
        private set(value) { _progress.value = value }

    private val _finished: MutableState<Boolean> = mutableStateOf(engine.isFinished)
    var finished: Boolean
        get() = _finished.value
        private set(value) { _finished.value = value }

    val groupCount: Int get() = engine.groupCount

    val practiceMode: PracticeMode get() = engine.practiceMode

    /** A/B 循环是否启用。 */
    val isLoopActive: Boolean get() = engine.isLoopActive

    /** 当前循环范围；未启用时返回 [0, totalTicks]。 */
    val loopRange: LongRange get() = engine.currentLoopStart..engine.currentLoopEnd

    /** 当前等待用户按下的音高（仅练习轨道）。 */
    val pendingPitches: Set<Int> get() = engine.pendingPitches()

    val activeKeys: Set<Int> get() = engine.activeKeys()

    /** 当前物理按下的音高（可观察 State，供 PianoRoll draw 阶段读取以高亮反馈）。 */
    private val _heldPitches: MutableState<Set<Int>> = mutableStateOf(emptySet())
    var heldPitches: Set<Int>
        get() = _heldPitches.value
        private set(value) { _heldPitches.value = value }

    /**
     * 动态节拍脉冲强度（Phase 3 文档 6.4）。
     * 范围 0..1：长辈连续按对并接近 BPM 时接近 1.0，卡住找键时衰减到 0.15 左右。
     */
    private val _pulseIntensity: MutableState<Float> = mutableStateOf(0.15f)
    var pulseIntensity: Float
        get() = _pulseIntensity.value
        private set(value) { _pulseIntensity.value = value }

    /** 被系统托管的轨道（UI 应将其透明度降至 0.3）；BOTH 模式为 null。 */
    val hostedTrack: TrackType? get() = when (practiceMode) {
        PracticeMode.BOTH -> null
        PracticeMode.RIGHT_ONLY -> TrackType.LEFT_HAND
        PracticeMode.LEFT_ONLY -> TrackType.RIGHT_HAND
    }

    init {
        synth.start()
        playHosted(engine.arrive())
        sync()
        startPulseDecay()
    }

    /** 启动脉冲强度衰减协程： progress 推进时点亮，卡顿时逐渐变暗。 */
    private fun startPulseDecay() {
        scope.launch {
            while (true) {
                delay(PULSE_DECAY_MS)
                if (!finished) {
                    pulseIntensity = (pulseIntensity * PULSE_DECAY_FACTOR).coerceAtLeast(PULSE_MIN)
                }
            }
        }
    }

    // —— 准度计分（结算页用，过程不展示）——
    fun scorePercent(): Int = engine.scorePercent()
    fun cleanHitCount(): Int = engine.cleanHitCount()
    fun totalActiveNotes(): Int = engine.totalActiveNotes()
    fun wrongPressCount(): Int = engine.wrongPressCount()
    fun groupWrongCounts(): IntArray = engine.groupWrongCounts()

    fun onNoteOn(pitch: Int) {
        playHosted(engine.onNoteOn(pitch))
        sync()
    }

    fun onNoteOff(pitch: Int) {
        engine.onNoteOff(pitch)
        sync()
    }

    fun reset() {
        playHosted(engine.reset())
        sync()
    }

    /** 设置 A/B 循环范围；调用后当前进度会调整到循环起点附近。 */
    fun setLoop(startTick: Long, endTick: Long) {
        engine.setLoop(startTick, endTick)
        reset()
    }

    /** 清除 A/B 循环。 */
    fun clearLoop() {
        engine.clearLoop()
        reset()
    }

    private fun sync() {
        val prevProgress = progress
        currentTick = engine.currentTick
        progress = engine.progress
        finished = engine.isFinished
        heldPitches = engine.activeKeys()
        // A/B 循环回卷会导致 progress 倒退，此时应重置托管调度基准，避免延迟计算错乱
        if (progress < prevProgress) {
            lastHostedTick = -1
        }
        // 进度向前推进时点亮节拍脉冲；回卷时也重新点燃，提示继续练习
        if (progress != prevProgress) {
            pulseIntensity = 1f
            scheduleHostedIfNeeded()
        }
    }

    /**
     * 当游标停在纯托管组时，按真实节奏调度下一步推进。
     * 第一个托管组立即发声，后续托管组按 tick 间隔延迟。
     */
    private fun scheduleHostedIfNeeded() {
        hostedJob?.cancel()
        if (finished || engine.pendingPitches().isNotEmpty()) {
            lastHostedTick = -1
            return
        }
        val delayMs = if (lastHostedTick < 0) 0L else {
            ((engine.currentTick - lastHostedTick) * msPerTick).toLong().coerceAtLeast(0)
        }
        hostedJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            val notes = engine.advanceHosted()
            if (notes.isNotEmpty()) {
                playHosted(notes)
                sync()
            }
        }
    }

    private fun playHosted(notes: List<NoteEntity>) {
        if (notes.isEmpty()) return
        // 记录最后一个被处理托管组的起始 tick，供节奏调度用
        lastHostedTick = notes.maxOf { it.startTick }
        for (n in notes) {
            synth.noteOn(n.pitch, HOSTED_VELOCITY)
            val durMs = (n.durationTick * msPerTick).toLong().coerceIn(MIN_TONE_MS, MAX_TONE_MS)
            scope.launch {
                delay(durMs)
                synth.noteOff(n.pitch)
            }
        }
    }

    private companion object {
        const val HOSTED_VELOCITY = 80
        const val MIN_TONE_MS = 60L
        const val MAX_TONE_MS = 4000L
        const val PULSE_DECAY_MS = 50L
        const val PULSE_DECAY_FACTOR = 0.97f
        const val PULSE_MIN = 0.15f
    }
}
