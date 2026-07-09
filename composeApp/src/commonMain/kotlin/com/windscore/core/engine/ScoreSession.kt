package com.windscore.core.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.windscore.audio.AudioSynth
import com.windscore.core.model.NoteEntity
import com.windscore.core.model.PracticeMode
import com.windscore.core.model.Score
import com.windscore.core.model.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * [JudgmentEngine] 的 Compose 友好包装，并负责**托管轨道的自动发声**（文档 4.2）：
 * 游标每落到新组，由 [AudioSynth] 主动 noteOn 托管音，并按音符持续时长延迟 noteOff。
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

    init {
        synth.start()
        playHosted(engine.arrive())
    }

    var currentTick by mutableStateOf(engine.currentTick)
        private set

    var progress by mutableStateOf(engine.progress)
        private set

    var finished by mutableStateOf(engine.isFinished)
        private set

    val groupCount: Int get() = engine.groupCount

    val practiceMode: PracticeMode get() = engine.practiceMode

    /** 当前等待用户按下的音高（仅练习轨道）。 */
    val pendingPitches: Set<Int> get() = engine.pendingPitches()

    val activeKeys: Set<Int> get() = engine.activeKeys()

    /** 当前物理按下的音高（可观察 State，供 PianoRoll draw 阶段读取以高亮反馈）。 */
    var heldPitches by mutableStateOf<Set<Int>>(emptySet())
        private set

    /** 被系统托管的轨道（UI 应将其透明度降至 0.3）；BOTH 模式为 null。 */
    val hostedTrack: TrackType? get() = when (practiceMode) {
        PracticeMode.BOTH -> null
        PracticeMode.RIGHT_ONLY -> TrackType.LEFT_HAND
        PracticeMode.LEFT_ONLY -> TrackType.RIGHT_HAND
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

    private fun sync() {
        currentTick = engine.currentTick
        progress = engine.progress
        finished = engine.isFinished
        heldPitches = engine.activeKeys()
    }

    private fun playHosted(notes: List<NoteEntity>) {
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
    }
}
