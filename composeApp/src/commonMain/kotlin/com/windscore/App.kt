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
import com.windscore.core.loadScore
import com.windscore.core.model.PracticeMode
import com.windscore.core.model.Score
import com.windscore.midi.MidiEvent
import com.windscore.midi.MidiManager
import com.windscore.ui.PlayerScreen
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

    // 当乐谱打开且五线谱渲染完成后，在后台启动 (warmup) 声音合成器，防止与 WebView 初始化发生 native 死锁，也防止点击播放时卡顿
    LaunchedEffect(currentScore, renderMs) {
        val score = currentScore
        if (score != null) {
            val shouldWarmup = score.rawXml == null || renderMs != null
            if (shouldWarmup) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    synth.start()
                }
            }
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

    // 空格键播放/暂停（桌面端通过 AWT KeyEventDispatcher 触发）
    LaunchedEffect(spacePressed) {
        if (spacePressed > 0) {
            val s = session ?: return@LaunchedEffect
            if (!isPlaying) {
                val cursorTick = if (playTick > 0) playTick else s.currentTick
                player?.seekTo(cursorTick)
            }
            player?.toggle()
        }
    }

    fun openScore(score: Score) {
        currentScore = score
        practiceMode = PracticeMode.BOTH
        viewMode = ViewMode.SHEET_FULL
        renderMs = null
        pendingLoopStart = null
        session = ScoreSession(score, synth, scope, PracticeMode.BOTH)
        if (!recentScores.any { it.title == score.title && it.totalTicks == score.totalTicks }) {
            recentScores.add(0, score)
            if (recentScores.size > 10) recentScores.removeLast()
        }
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
                    onLoadDefault = { openScore(loadScore()) },
                    onScoreClick = { openScore(it) },
                )
            } else {
                val s = session ?: return@Surface
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
                    onSeek = { tick ->
                        if (isPlaying || playTick > 0) player?.seekTo(tick) else s.seekTo(tick)
                    },
                    onStepForward = {
                        if (isPlaying || playTick > 0) player?.stepForward() else s.stepForward()
                    },
                    onStepBackward = {
                        if (isPlaying || playTick > 0) player?.stepBackward() else s.stepBackward()
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
                )
            }
        }
    }
}
