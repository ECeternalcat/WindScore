package com.windscore.core.engine

import com.windscore.audio.AudioSynth
import com.windscore.core.model.NoteEntity
import com.windscore.core.model.PracticeMode
import com.windscore.core.model.Score
import com.windscore.core.model.TrackType
import com.windscore.core.traceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Immutable observable snapshot of a practice/scoring session.
 * UI collects [ScoreSession.state]; non-UI consumers may use the
 * convenience getters on [ScoreSession] (plain snapshot reads).
 */
data class SessionState(
    val currentTick: Long = 0L,
    val progress: Int = 0,
    val groupCount: Int = 0,
    val finished: Boolean = false,
    val pendingPitches: Set<Int> = emptySet(),
    val cuePitches: Set<Int> = emptySet(),
    val hostedCuePitches: Set<Int> = emptySet(),
    val heldPitches: Set<Int> = emptySet(),
    val hitNoteIds: Set<Int> = emptySet(),
    val pulseIntensity: Float = 0.15f,
    val hostedEnabled: Boolean = true,
)

/**
 * [JudgmentEngine] 的状态流友好包装，并负责**托管轨道的自动发声**（文档 4.2）：
 * 游标每落到新组，由 [AudioSynth] 主动 noteOn 托管音，并按音符持续时长延迟 noteOff。
 *
 * 额外责任：对连续纯托管段按真实节奏定时推进，避免瞬时 burst。
 *
 * 状态以单一 [SessionState] 快照流暴露（对齐控制器层的 StateFlow 约定，
 * 不再依赖 Compose runtime）。托管开关通过 [setHostedEnabled] 显式设置。
 */
class ScoreSession(
    val score: Score,
    private val synth: AudioSynth,
    private val scope: CoroutineScope,
    practiceMode: PracticeMode = PracticeMode.BOTH,
) {
    internal val traceId: Long = nextTraceId++
    private val engine = JudgmentEngine(score, practiceMode)
    private val sessionScope = CoroutineScope(scope.coroutineContext + SupervisorJob())

    private val msPerTick: Double = run {
        val tempo = score.tempo.takeIf { it > 0 } ?: 120
        val div = score.divisions.takeIf { it > 0 } ?: 4
        60_000.0 / (tempo * div)
    }

    /** 托管段自动推进任务；遇到练习组或结束时取消。 */
    /** Hosted play control switch. Set to false when MIDI player is running. */
    private var hostedJob: Job? = null

    /** 上一个已处理托管组的起始 tick，用于计算下一托管组的节奏间隔。 */
    private var lastHostedTick: Long = -1

    private val _state = MutableStateFlow(buildState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    // —— 快照便捷读取（非订阅式；UI 请收集 state）——
    val currentTick: Long get() = _state.value.currentTick
    val progress: Int get() = _state.value.progress
    val finished: Boolean get() = _state.value.finished
    val groupCount: Int get() = _state.value.groupCount
    val pendingPitches: Set<Int> get() = _state.value.pendingPitches
    val cuePitches: Set<Int> get() = _state.value.cuePitches
    val hostedCuePitches: Set<Int> get() = _state.value.hostedCuePitches
    val heldPitches: Set<Int> get() = _state.value.heldPitches
    val hitNoteIds: Set<Int> get() = _state.value.hitNoteIds
    val pulseIntensity: Float get() = _state.value.pulseIntensity
    val isHostedEnabled: Boolean get() = _state.value.hostedEnabled

    val practiceMode: PracticeMode get() = engine.practiceMode

    /** A/B 循环是否启用。 */
    val isLoopActive: Boolean get() = engine.isLoopActive

    /** 当前循环范围；未启用时返回 [0, totalTicks]。 */
    val loopRange: LongRange get() = engine.currentLoopStart..engine.currentLoopEnd

    val activeKeys: Set<Int> get() = engine.activeKeys()

    /** 被系统托管的轨道（UI 应将其透明度降至 0.3）；BOTH 模式为 null。 */
    val hostedTrack: TrackType? get() = when (practiceMode) {
        PracticeMode.BOTH -> null
        PracticeMode.RIGHT_ONLY -> TrackType.LEFT_HAND
        PracticeMode.LEFT_ONLY -> TrackType.RIGHT_HAND
    }

    init {
        startPulseDecay()
    }

    fun close() {
        sessionScope.cancel()
    }

    /** 显式控制托管发声开关（替代曾经的可变公开字段，跨对象写入安全）。 */
    fun setHostedEnabled(enabled: Boolean) {
        _state.update { it.copy(hostedEnabled = enabled) }
    }

    /** 启动脉冲强度衰减协程： progress 推进时点亮，卡顿时逐渐变暗。 */
    private fun startPulseDecay() {
        sessionScope.launch {
            while (true) {
                delay(PULSE_DECAY_MS)
                if (!_state.value.finished) {
                    _state.update { s ->
                        s.copy(pulseIntensity = (s.pulseIntensity * PULSE_DECAY_FACTOR).coerceAtLeast(PULSE_MIN))
                    }
                }
            }
        }
    }

    private fun buildState() = SessionState(
        currentTick = engine.currentTick,
        progress = engine.progress,
        groupCount = engine.groupCount,
        finished = engine.isFinished,
        pendingPitches = engine.pendingPitches(),
        cuePitches = engine.cuePitches(),
        hostedCuePitches = engine.hostedCuePitches(),
        heldPitches = engine.activeKeys(),
        hitNoteIds = engine.hitNoteIds(),
        pulseIntensity = PULSE_MIN,
        hostedEnabled = true,
    )

    // —— 准度计分（结算页用，过程不展示）——
    fun scorePercent(): Int = engine.scorePercent()
    fun cleanHitCount(): Int = engine.cleanHitCount()
    fun totalActiveNotes(): Int = engine.totalActiveNotes()
    fun wrongPressCount(): Int = engine.wrongPressCount()
    fun groupWrongCounts(): IntArray = engine.groupWrongCounts()

    private var interactiveStarted = false

    fun onNoteOn(pitch: Int) {
        trace("noteOn($pitch) before")
        interactiveStarted = true
        playHosted(engine.onNoteOn(pitch))
        sync()
        trace("noteOn($pitch) after")
        // 用户交互后无条件武装托管推进：覆盖 seek/reset 后停在纯托管组的死锁场景
        // （该组不调用 step()、progress 不变，sync 内的调度不会触发）。
        scheduleHostedIfNeeded()
    }

    fun onNoteOff(pitch: Int) {
        trace("noteOff($pitch) before")
        engine.onNoteOff(pitch)
        sync()
        trace("noteOff($pitch) after")
    }

    fun reset(playAudio: Boolean = false) {
        trace("reset(playAudio=$playAudio) before")
        interactiveStarted = false
        val notes = engine.reset()
        if (playAudio) playHosted(notes)
        sync()
        trace("reset after")
    }

    /** 定点跳转（文档 3.3）。 */
    fun seekTo(tick: Long) {
        trace("seekTo($tick) before")
        interactiveStarted = false
        engine.seekTo(tick)
        sync()
        trace("seekTo($tick) after")
    }

    /** 微观步进：前进一个音符组（文档 3.3 盲操微调）。 */
    fun stepForward() {
        trace("stepForward before")
        interactiveStarted = false
        engine.stepForward()
        sync()
        trace("stepForward after")
    }

    /** 微观步进：后退一个音符组（文档 3.3 盲操微调）。 */
    fun stepBackward() {
        trace("stepBackward before")
        interactiveStarted = false
        engine.stepBackward()
        sync()
        trace("stepBackward after")
    }

    /** 设置 A/B 循环范围；调用后当前进度会调整到循环起点附近。 */
    fun setLoop(startTick: Long, endTick: Long, playAudio: Boolean = false) {
        engine.setLoop(startTick, endTick)
        reset(playAudio)
    }

    /** 清除 A/B 循环。 */
    fun clearLoop(playAudio: Boolean = false) {
        engine.clearLoop()
        reset(playAudio)
    }

    private fun sync() {
        val snapshot = _state.value
        val newState = snapshot.copy(
            currentTick = engine.currentTick,
            progress = engine.progress,
            finished = engine.isFinished,
            heldPitches = engine.activeKeys(),
            hitNoteIds = engine.hitNoteIds(),
            pendingPitches = engine.pendingPitches(),
            cuePitches = engine.cuePitches(),
            hostedCuePitches = engine.hostedCuePitches(),
        )
        // A/B 循环回卷会导致 progress 倒退，此时应重置托管调度基准，避免延迟计算错乱
        if (newState.progress < snapshot.progress) {
            lastHostedTick = -1
        }
        // 进度向前推进时点亮节拍脉冲；回卷时也重新点燃，提示继续练习
        val progressChanged = newState.progress != snapshot.progress
        _state.value = if (progressChanged) newState.copy(pulseIntensity = 1f) else newState
        if (progressChanged) {
            scheduleHostedIfNeeded()
        }
    }

    /**
     * 当游标停在纯托管组时，按真实节奏调度下一步推进。
     * 第一个托管组立即发声，后续托管组按 tick 间隔延迟。
     */
    private fun scheduleHostedIfNeeded() {
        hostedJob?.cancel()
        if (!_state.value.hostedEnabled || !interactiveStarted || _state.value.finished ||
            engine.pendingPitches().isNotEmpty()
        ) {
            lastHostedTick = -1
            return
        }
        val delayMs = if (lastHostedTick < 0) 0L else {
            ((engine.currentTick - lastHostedTick) * msPerTick).toLong().coerceAtLeast(0)
        }
        hostedJob = sessionScope.launch {
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
            sessionScope.launch {
                delay(durMs)
                synth.noteOff(n.pitch)
            }
        }
    }

    private fun trace(action: String) {
        val s = _state.value
        traceState(
            "session=$traceId action=$action tick=${s.currentTick} progress=${s.progress}/${s.groupCount} " +
                "pending=${s.pendingPitches.sorted()} cue=${s.cuePitches.sorted()} " +
                "hostedCue=${s.hostedCuePitches.sorted()} held=${s.heldPitches.sorted()} finished=${s.finished}",
        )
    }

    private companion object {
        var nextTraceId = 1L
        const val HOSTED_VELOCITY = 80
        const val MIN_TONE_MS = 60L
        const val MAX_TONE_MS = 4000L
        const val PULSE_DECAY_MS = 50L
        const val PULSE_DECAY_FACTOR = 0.97f
        const val PULSE_MIN = 0.15f
    }
}
