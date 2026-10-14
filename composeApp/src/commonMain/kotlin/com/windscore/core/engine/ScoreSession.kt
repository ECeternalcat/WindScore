package com.windscore.core.engine

import com.windscore.audio.AudioSynth
import com.windscore.core.model.NoteEntity
import com.windscore.core.model.PracticeMode
import com.windscore.core.model.Score
import com.windscore.core.model.TrackType
import com.windscore.core.model.hostedTrack
import com.windscore.core.model.trackSelection
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
import kotlin.math.ceil

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

enum class ProgressOrigin { USER, HOSTED }

/** Wait-engine position evidence, never score/MIDI alignment or whole-region traversal proof. */
data class SessionProgress(
    val origin: ProgressOrigin,
    val completedGroupTick: Long,
    val completedGroup: Int,
    val boundaryTick: Long,
    val completedPass: Boolean,
    val fullCompletion: Boolean,
    val nextTick: Long,
)

class ScoreSession(
    val score: Score,
    private val synth: AudioSynth,
    private val scope: CoroutineScope,
    practiceMode: PracticeMode = PracticeMode.BOTH,
    policy: JudgmentPolicy = WaitModeJudgment,
) {
    internal val traceId: Long = SessionLane.run { nextTraceId++ }
    val practiceMode = practiceMode
    private val trackSelection = practiceMode.trackSelection()
    private val engine = JudgmentEngine(score, trackSelection, policy)
    private val sessionScope = CoroutineScope(scope.coroutineContext + SupervisorJob())
    private val baseMsPerTick = 60_000.0 / ((score.tempo.takeIf { it > 0 } ?: 120) * (score.divisions.takeIf { it > 0 } ?: 4))
    private var speed = 1f
    private var closed = false
    private var hostedJob: Job? = null
    private var hostedGeneration = 0L
    private var lastHostedTick = -1L
    // Explicit wrap origin: do not confuse a loop wrap with an unset scheduling baseline.
    private var hostedWrapFromTick: Long? = null
    private var interactiveStarted = false
    private val _state = MutableStateFlow(SessionState(groupCount = engine.groupCount))
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Synchronous, ordered, exception-isolated; invoked after input append and engine advancement. */
    var onProgress: ((ScoreSession, SessionProgress) -> Unit)? = null

    val currentTick get() = _state.value.currentTick
    val progress get() = _state.value.progress
    val finished get() = _state.value.finished
    val groupCount get() = _state.value.groupCount
    val pendingPitches get() = _state.value.pendingPitches
    val cuePitches get() = _state.value.cuePitches
    val hostedCuePitches get() = _state.value.hostedCuePitches
    val heldPitches get() = _state.value.heldPitches
    val hitNoteIds get() = _state.value.hitNoteIds
    val pulseIntensity get() = _state.value.pulseIntensity
    val isHostedEnabled get() = _state.value.hostedEnabled
    val isLoopActive get() = SessionLane.run { engine.isLoopActive }
    val loopRange get() = SessionLane.run { engine.currentLoopStart..engine.currentLoopEnd }
    val activeKeys get() = SessionLane.run { engine.activeKeys() }
    val hostedTrack: TrackType? get() = trackSelection.hostedTrack

    init {
        sync()
        sessionScope.launch {
            while (true) {
                delay(50)
                _state.update { it.copy(pulseIntensity = (it.pulseIntensity * 0.97f).coerceAtLeast(0.15f)) }
            }
        }
    }

    fun close() = SessionLane.run {
        closed = true
        onProgress = null
        hostedGeneration++
        sessionScope.cancel()
    }

    fun setHostedEnabled(enabled: Boolean) = SessionLane.run {
        if (closed) return@run
        _state.update { it.copy(hostedEnabled = enabled) }
        scheduleHostedIfNeeded()
    }

    fun setPracticeSpeed(value: Float) = SessionLane.run {
        if (closed) return@run
        speed = value.coerceIn(0f, 2f)
        scheduleHostedIfNeeded()
    }

    fun scorePercent() = SessionLane.run { engine.scorePercent() }
    fun cleanHitCount() = SessionLane.run { engine.cleanHitCount() }
    fun totalActiveNotes() = engine.totalActiveNotes()
    fun wrongPressCount() = SessionLane.run { engine.wrongPressCount() }
    fun groupWrongCounts() = SessionLane.run { engine.groupWrongCounts() }

    fun onNoteOn(pitch: Int) = SessionLane.run {
        // Passthrough may sound during player playback, but cannot fight its position/judgment.
        if (closed || !isHostedEnabled || finished) return@run
        interactiveStarted = true
        trace("noteOn($pitch) before")
        playHosted(engine.onNoteOn(pitch, allowHostedAdvance = speed > 0))
        syncAndNotify(ProgressOrigin.USER)
        trace("noteOn($pitch) after")
        scheduleHostedIfNeeded()
    }

    fun onNoteOff(pitch: Int) = SessionLane.run {
        if (closed || !isHostedEnabled) return@run
        engine.onNoteOff(pitch)
        sync()
    }

    fun reset(playAudio: Boolean = false) = SessionLane.run {
        if (closed) return@run
        interactiveStarted = false
        lastHostedTick = -1
        hostedWrapFromTick = null
        val notes = engine.reset()
        if (playAudio) playHosted(notes)
        sync()
        scheduleHostedIfNeeded()
    }

    fun seekTo(tick: Long) = SessionLane.run {
        if (closed) return@run
        interactiveStarted = false
        lastHostedTick = -1
        hostedWrapFromTick = null
        engine.seekTo(tick)
        sync()
        scheduleHostedIfNeeded()
    }

    fun stepForward() = SessionLane.run {
        if (closed) return@run
        interactiveStarted = false
        lastHostedTick = -1
        hostedWrapFromTick = null
        engine.stepForward()
        sync()
        scheduleHostedIfNeeded()
    }

    fun stepBackward() = SessionLane.run {
        if (closed) return@run
        interactiveStarted = false
        lastHostedTick = -1
        hostedWrapFromTick = null
        engine.stepBackward()
        sync()
        scheduleHostedIfNeeded()
    }

    fun setLoop(startTick: Long, endTick: Long, playAudio: Boolean = false) = SessionLane.run {
        if (closed) return@run
        engine.setLoop(startTick, endTick)
        reset(playAudio)
    }

    fun clearLoop(playAudio: Boolean = false) = SessionLane.run {
        if (closed) return@run
        engine.clearLoop()
        reset(playAudio)
    }

    private fun trace(action: String) {
        val s = _state.value
        traceState("session=$traceId action=$action tick=${s.currentTick} progress=${s.progress}/${s.groupCount} " +
            "pending=${s.pendingPitches.sorted()} cue=${s.cuePitches.sorted()} hostedCue=${s.hostedCuePitches.sorted()} " +
            "held=${s.heldPitches.sorted()} finished=${s.finished}")
    }

    private fun sync() {
        _state.update { it.copy(
            currentTick = engine.currentTick,
            progress = engine.progress,
            finished = engine.isFinished,
            heldPitches = engine.activeKeys(),
            hitNoteIds = engine.hitNoteIds(),
            pendingPitches = engine.pendingPitches(),
            cuePitches = engine.cuePitches(),
            hostedCuePitches = engine.hostedCuePitches(),
        ) }
    }

    private fun syncAndNotify(origin: ProgressOrigin) {
        val transitions = engine.drainCompletedGroups()
        val pass = transitions.lastOrNull { it.completedPass }
        if (pass != null) {
            hostedWrapFromTick = pass.tick
        } else if (transitions.isNotEmpty()) {
            hostedWrapFromTick = null
        }
        sync()
        if (transitions.isNotEmpty()) _state.update { it.copy(pulseIntensity = 1f) }
        transitions.forEach { group ->
            runCatching { onProgress?.invoke(this, SessionProgress(
                origin, group.tick, group.group, group.boundaryTick,
                group.completedPass, group.fullCompletion, currentTick,
            )) }.onFailure { println("[SessionProgress] Listener failed: ${it.message}") }
        }
    }

    private fun scheduleHostedIfNeeded() {
        val generation = ++hostedGeneration
        hostedJob?.cancel()
        if (closed || !isHostedEnabled || !interactiveStarted || finished || speed <= 0 || engine.pendingPitches().isNotEmpty()) return
        val intervalTicks = hostedWrapFromTick?.let { completedTick ->
            engine.currentLoopEnd - completedTick + engine.currentTick - engine.currentLoopStart
        } ?: if (lastHostedTick < 0) 0L else engine.currentTick - lastHostedTick
        // A positive musical interval must never truncate into a zero-delay hosted loop.
        val delayMs = if (intervalTicks <= 0) 0L else
            ceil(intervalTicks * baseMsPerTick / speed).toLong().coerceAtLeast(1)
        hostedJob = sessionScope.launch {
            if (delayMs > 0) delay(delayMs)
            SessionLane.run {
                if (closed || generation != hostedGeneration || !isHostedEnabled || speed <= 0) return@run
                hostedWrapFromTick = null
                playHosted(engine.advanceHosted())
                syncAndNotify(ProgressOrigin.HOSTED)
                scheduleHostedIfNeeded()
            }
        }
    }

    private fun playHosted(notes: List<NoteEntity>) {
        if (notes.isEmpty() || !isHostedEnabled || speed <= 0) return
        lastHostedTick = notes.maxOf { it.startTick }
        for (note in notes) {
            synth.noteOn(note.pitch, 80)
            val duration = (note.durationTick * baseMsPerTick / speed).toLong().coerceIn(60, 4000)
            sessionScope.launch { delay(duration); synth.noteOff(note.pitch) }
        }
    }

    private companion object { var nextTraceId = 1L }
}
