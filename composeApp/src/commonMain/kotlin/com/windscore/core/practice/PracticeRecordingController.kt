package com.windscore.core.practice

import com.windscore.ViewMode
import com.windscore.core.AppDiagnostic
import com.windscore.core.DiagnosticController
import com.windscore.core.RecordingDiagnosticOperation
import com.windscore.core.cleanupAttemptTempFiles
import com.windscore.core.loadPracticeAttempts
import com.windscore.core.model.PracticeMode
import com.windscore.core.model.Score
import com.windscore.core.nowEpochMillis
import com.windscore.core.savePracticeAttempt
import com.windscore.core.engine.ScoreSession
import com.windscore.core.engine.SessionLane
import com.windscore.core.engine.SessionProgress
import com.windscore.midi.MidiEvent
import com.windscore.midi.MidiRecordingSink
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** session supplies current normalized position; no Compose-collected tick is used. */
data class AttemptConditions(
    val score: Score,
    val mode: PracticeMode,
    val viewMode: ViewMode,
    val loopRange: LongRange?,
    val speed: Float,
    val session: ScoreSession? = null,
    val startTick: Long? = null,
)

interface AttemptDataSource {
    suspend fun saveAttempt(attempt: PracticeAttempt)
    suspend fun loadAttempts(scoreId: String): List<PracticeAttempt>
    suspend fun cleanupTempFiles(): Int
}
private object PlatformAttemptDataSource : AttemptDataSource {
    override suspend fun saveAttempt(attempt: PracticeAttempt) = savePracticeAttempt(attempt)
    override suspend fun loadAttempts(scoreId: String) = loadPracticeAttempts(scoreId)
    override suspend fun cleanupTempFiles() = cleanupAttemptTempFiles()
}

class PracticeRecordingController(
    private val scope: CoroutineScope,
    private val dataSource: AttemptDataSource = PlatformAttemptDataSource,
    private val diagnostics: DiagnosticController? = null,
    clock: MonotonicMicrosClock = SystemMonotonicMicrosClock,
    private val wallClockMillis: () -> Long = { nowEpochMillis() },
    private val closeTimeoutMs: Long = 5_000,
) : MidiRecordingSink {
    private val saveScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var saveFailure: Throwable? = null // accessed only by queue worker
    private val worker = saveScope.launch {
        try { dataSource.cleanupTempFiles() } catch (error: Exception) { report(RecordingDiagnosticOperation.SAVE, error) }
        for (operation in queue) operation()
    }
    private val recorder = AttemptRecorder(clock, wallClockMillis, listener = ::persist)
    private var live: AttemptConditions? = null
    private var type = AttemptType.PRACTICE
    private var midiEnabled = true
    private var playing = false
    private var completed = false
    private var closed = false
    private var changing = false

    val activeAttemptId get() = recorder.activeAttemptId

    fun beginPracticeSession(conditions: AttemptConditions) = SessionLane.run {
        if (closed) return@run
        detach()
        recorder.startSession()
        completed = false
        type = AttemptType.PRACTICE
        attach(conditions)
        arm()
    }

    /** Finalize OLD evidence before mutation; rearm from a fresh POST-command snapshot. */
    fun changePractice(reason: AttemptEndReason = AttemptEndReason.USER_STOPPED, change: () -> AttemptConditions?) = SessionLane.run {
        if (closed || live == null) return@run
        finish(reason)
        changing = true
        try {
            completed = false
            change()?.let(::attach)
        } finally { changing = false }
        arm()
    }

    fun restartPracticeAttempt(conditions: AttemptConditions) = changePractice(AttemptEndReason.USER_RESTARTED) { conditions }
    fun practiceConditionsChanged(conditions: AttemptConditions) = changePractice { conditions }

    fun scoringStarted(conditions: AttemptConditions) = scoringStarted { conditions }

    fun scoringStarted(change: () -> AttemptConditions?) = SessionLane.run {
        if (closed || live == null) return@run
        finish(AttemptEndReason.USER_STOPPED)
        changing = true
        try {
            type = AttemptType.SCORING
            completed = false
            change()?.let(::attach)
        } finally { changing = false }
        arm()
    }

    fun scoringCompleted() = SessionLane.run {
        if (type == AttemptType.SCORING && !completed) {
            finish(AttemptEndReason.COMPLETED)
            completed = true
        }
    }

    fun practiceResumed(conditions: AttemptConditions) = practiceResumed { conditions }

    fun practiceResumed(change: () -> AttemptConditions?) = SessionLane.run {
        if (closed || live == null) return@run
        finish(AttemptEndReason.USER_STOPPED)
        changing = true
        try {
            type = AttemptType.PRACTICE
            change()?.let { completed = it.session?.finished ?: false; attach(it) }
        } finally { changing = false }
        arm()
    }

    override fun onMidiEnabledChanged(enabled: Boolean) = SessionLane.run {
        midiEnabled = enabled
        if (!enabled) finish(AttemptEndReason.USER_STOPPED) else arm()
    }

    fun setPlaybackActive(active: Boolean) = SessionLane.run {
        if (playing == active) return@run
        playing = active
        if (active) finish(AttemptEndReason.USER_STOPPED) else arm()
    }

    fun endSession() = SessionLane.run {
        finish(AttemptEndReason.NAVIGATED_AWAY)
        detach()
        live = null
        completed = true
    }

    /** Nonblocking: independent worker drains up to 5 seconds, even after UI scope cancellation. */
    fun close() = SessionLane.run {
        if (closed) return@run
        finish(AttemptEndReason.APP_INTERRUPTED)
        detach()
        live = null
        closed = true
        queue.close()
        saveScope.launch {
            try { withTimeout(closeTimeoutMs.coerceIn(1, 5_000)) { worker.join() } }
            catch (error: Exception) { report(RecordingDiagnosticOperation.SAVE, error) }
            finally { saveScope.cancel() }
        }
    }

    override fun onMidiEvent(event: MidiEvent) = SessionLane.run {
        val session = live?.session
        recorder.onMidiEvent(event, session?.currentTick, session?.progress)
    }

    override fun onMidiDisconnected() = SessionLane.run {
        midiEnabled = false
        recorder.onMidiDisconnected()
    }

    private fun attach(conditions: AttemptConditions) {
        detach()
        live = conditions
        conditions.session?.onProgress = { session, event ->
            SessionLane.run {
                if (!closed && !changing && live?.session === session && midiEnabled && !playing && !completed) progress(event)
            }
        }
    }

    private fun detach() { live?.session?.onProgress = null }

    private fun progress(event: SessionProgress) {
        recorder.reached(event.completedGroupTick, event.completedGroupTick)
        if (event.completedPass || event.fullCompletion) {
            recorder.finishAttempt(AttemptEndReason.COMPLETED, event.boundaryTick)
            completed = event.fullCompletion
            if (event.completedPass) arm()
        }
    }

    private fun finish(reason: AttemptEndReason) {
        recorder.finishAttempt(reason, live?.session?.currentTick)
    }

    private fun arm() {
        val conditions = live ?: return
        if (closed || changing || completed || !midiEnabled || playing || recorder.activeAttemptId != null || conditions.session?.finished == true) return
        recorder.startAttempt(buildContext(conditions))
    }

    /** Barrier fails if any finalized snapshot was not durable; no silent successful export. */
    suspend fun awaitPendingWrites(timeoutMs: Long = 5_000) = withTimeout(timeoutMs) {
        val result = CompletableDeferred<Unit>()
        queue.send { saveFailure?.let { result.completeExceptionally(it) } ?: result.complete(Unit) }
        result.await()
    }

    suspend fun loadDurableAttempts(scoreId: String): List<PracticeAttempt> {
        val result = CompletableDeferred<List<PracticeAttempt>>()
        queue.send {
            try {
                saveFailure?.let { throw it }
                result.complete(dataSource.loadAttempts(scoreId))
            } catch (error: Exception) { result.completeExceptionally(error) }
        }
        return withTimeout(5_000) { result.await() }
    }

    fun loadAttempts(scoreId: String, onResult: (List<PracticeAttempt>) -> Unit) {
        scope.launch {
            val attempts = try { loadDurableAttempts(scoreId) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                report(RecordingDiagnosticOperation.LOAD, error)
                emptyList()
            }
            onResult(attempts)
        }
    }

    private fun persist(attempt: PracticeAttempt) {
        if (attempt.events.isEmpty()) return
        val sent = queue.trySend {
            try { dataSource.saveAttempt(attempt) }
            catch (error: Exception) { saveFailure = error; report(RecordingDiagnosticOperation.SAVE, error) }
        }
        if (sent.isFailure) report(RecordingDiagnosticOperation.SAVE, IllegalStateException("Recording save queue closed"))
    }

    private fun report(operation: RecordingDiagnosticOperation, error: Throwable) {
        // Essential reporting survives disappearance of the UI toast collector.
        println("[AttemptRecording] $operation failed: ${error.message}")
        diagnostics?.report(AppDiagnostic.Recording(operation, error))
    }

    private fun buildContext(conditions: AttemptConditions): PracticeContext {
        val score = conditions.score
        val region = if (type == AttemptType.SCORING) 0L..score.totalTicks else conditions.loopRange ?: 0L..score.totalTicks
        val speed = if (type == AttemptType.SCORING) 1f else conditions.speed
        val tempo = score.tempo.takeIf { it > 0 }?.let { (it * speed).roundToInt() }?.takeIf { it > 0 }
        return PracticeContext(
            scoreId = score.id, scoreVersion = score.version,
            regionStartTick = region.first, regionEndTick = region.last,
            practiceMode = conditions.mode, hintMode = conditions.viewMode.toHintMode(),
            requestedTempo = tempo, observedTempo = null,
            practiceModeType = conditions.viewMode.toPracticeModeType(), attemptType = type,
            startedAt = wallClockMillis(), startTick = conditions.session?.currentTick ?: conditions.startTick,
            divisions = score.divisions, speed = speed,
            effectiveHostedTempo = if (speed > 0) ((score.tempo.takeIf { it > 0 } ?: 120) * speed).roundToInt() else null,
        )
    }
}
