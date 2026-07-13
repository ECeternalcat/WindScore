package com.windscore

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.windscore.audio.createAudioSynth
import com.windscore.core.engine.ScorePlayer
import com.windscore.core.engine.ScoreSession
import com.windscore.core.model.PracticeMode
import com.windscore.core.model.Score
import com.windscore.midi.MidiEvent
import com.windscore.midi.MidiManager
import com.windscore.ui.PlayerScreen
import com.windscore.ui.SettingsScreen
import com.windscore.ui.WelcomeScreen
import com.windscore.ui.theme.WindScoreColors
import com.windscore.ui.theme.WindScoreTheme
import kotlinx.coroutines.launch

enum class ViewMode { ROLL, SHEET_KEYBOARD, SHEET_FULL }

@Composable
fun App(
    midiManager: MidiManager,
    isCompact: Boolean = false,
    spacePressed: Int = 0,
) {
    var currentScore by remember { mutableStateOf<Score?>(null) }
    var session by remember { mutableStateOf<ScoreSession?>(null) }
    var practiceMode by remember { mutableStateOf(PracticeMode.BOTH) }
    var viewMode by remember { mutableStateOf(ViewMode.SHEET_FULL) }
    var renderMs by remember { mutableStateOf<Double?>(null) }
    var pendingLoopStart by remember { mutableStateOf<Long?>(null) }
    var running by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val recentScores = remember { mutableStateListOf<Score>() }

    val synth = remember { createAudioSynth() }
    val scope = rememberCoroutineScope()
    val player = remember(currentScore) { currentScore?.let { ScorePlayer(it, synth, scope) } }
    val noTickFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(0L) }
    val noPlayFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
    val noPitchesFlow = remember { kotlinx.coroutines.flow.MutableStateFlow<Set<Int>>(emptySet()) }
    val playTick by (player?.tick ?: noTickFlow).collectAsState()
    val isPlaying by (player?.playing ?: noPlayFlow).collectAsState()
    val playingPitches by (player?.playingPitches ?: noPitchesFlow).collectAsState()

    // 自动清理 Session 协程
    DisposableEffect(session) {
        onDispose {
            session?.close()
        }
    }

    // App 启动即预热合成器，避免首次播放延迟 1 秒（Java Sound open 耗时）
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            synth.start()
        }
    }

    // 退出乐谱时，自动清理释放声音合成器资源
    DisposableEffect(currentScore) {
        onDispose {
            if (currentScore == null) {
                synth.stop()
            }
        }
    }

    LaunchedEffect(running) {
        if (running) {
            synth.start()
            midiManager.start()
            try {
                midiManager.events.collect { ev ->
                    when (ev) {
                        is MidiEvent.NoteOn -> {
                            synth.noteOn(ev.pitch, ev.velocity)
                            session?.onNoteOn(ev.pitch)
                        }
                        is MidiEvent.NoteOff -> {
                            synth.noteOff(ev.pitch)
                            session?.onNoteOff(ev.pitch)
                        }
                    }
                }
            } finally {
                midiManager.stop()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            midiManager.stop()
            synth.stop()
        }
    }
    LaunchedEffect(Unit) {
        com.windscore.core.loadRecentScores { loaded ->
            recentScores.clear()
            recentScores.addAll(loaded)
        }
    }

    fun openScore(score: Score) {
        currentScore = score
        practiceMode = PracticeMode.BOTH
        viewMode = ViewMode.SHEET_FULL
        renderMs = null
        pendingLoopStart = null
        session = ScoreSession(score, synth, scope, PracticeMode.BOTH)
        val existingIndex = recentScores.indexOfFirst { it.title == score.title && it.totalTicks == score.totalTicks }
        if (existingIndex >= 0) {
            recentScores.removeAt(existingIndex)
        }
        recentScores.add(0, score)
        if (recentScores.size > 10) recentScores.removeLast()
        com.windscore.core.saveRecentScores(recentScores)
    }

    fun closeScore() {
        player?.stop()
        session = null
        currentScore = null
    }

    fun resetSession() {
        session?.reset()
    }

    fun setPracticeMode(mode: PracticeMode) {
        practiceMode = mode
        currentScore?.let { openScore(it) }
    }

    WindScoreTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = WindScoreColors.canvas,
        ) {
            if (currentScore == null) {
                WelcomeScreen(
                    isCompact = isCompact,
                    recentScores = recentScores,
                    onScoreClick = { openScore(it) },
                    onRename = { score, newName ->
                        val idx = recentScores.indexOfFirst { it.title == score.title && it.totalTicks == score.totalTicks }
                        if (idx >= 0) {
                            recentScores[idx] = recentScores[idx].copy(title = newName)
                            com.windscore.core.saveRecentScores(recentScores)
                        }
                    },
                    onDelete = { score ->
                        recentScores.removeAll { it.title == score.title && it.totalTicks == score.totalTicks }
                        com.windscore.core.saveRecentScores(recentScores)
                    },
                )
            } else {
                val s = session ?: return@Surface

                // 播放中实时同步进度到练习 Session，并在暂停时做最终同步确保进度对齐
                LaunchedEffect(isPlaying, playTick) {
                    if (isPlaying) {
                        s.isHostedEnabled = false
                        s.seekTo(playTick)
                    } else {
                        s.isHostedEnabled = true
                        s.seekTo(playTick)
                    }
                }

                PlayerScreen(
                    score = s.score,
                    session = s,
                    isCompact = isCompact,
                    running = running,
                    practiceMode = practiceMode,
                    viewMode = viewMode,
                    pendingLoopStart = pendingLoopStart,
                    player = player,
                    playTick = playTick,
                    isPlaying = isPlaying,
                    playingPitches = playingPitches,
                    renderMs = renderMs,
                    spacePressed = spacePressed,
                    onSeek = { tick ->
                        player?.seekTo(tick)
                        s.seekTo(tick)
                    },
                    onStepForward = {
                        player?.stepForward()
                        s.stepForward()
                    },
                    onStepBackward = {
                        player?.stepBackward()
                        s.stepBackward()
                    },
                    onBack = { closeScore() },
                    onToggleMidi = {
                        running = !running
                    },
                    onReset = { resetSession() },
                    onModeChange = { setPracticeMode(it) },
                    onViewChange = { viewMode = it },
                    onLoopStart = { pendingLoopStart = s.currentTick },
                    onLoopEnd = {
                        s.setLoop(pendingLoopStart ?: 0L, s.currentTick)
                        pendingLoopStart = null
                    },
                    onClearLoop = {
                        s.clearLoop()
                        pendingLoopStart = null
                    },
                    onTogglePlay = {
                        if (!isPlaying) {
                            val cursorTick = if (playTick > 0) playTick else s.currentTick
                            player?.seekTo(cursorTick)
                        }
                        player?.toggle()
                    },
                    onStopPlay = { player?.stop() },
                    onRenderTime = { renderMs = it },
                    onShowSettings = { showSettings = true },
                )

                if (showSettings) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = WindScoreColors.canvas,
                    ) {
                        SettingsScreen(onBack = { showSettings = false })
                    }
                }
            }
        }
    }
}
