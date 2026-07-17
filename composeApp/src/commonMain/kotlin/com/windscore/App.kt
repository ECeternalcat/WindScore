package com.windscore

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import org.jetbrains.compose.resources.painterResource
import windscore.composeapp.generated.resources.Res
import windscore.composeapp.generated.resources.piano_keys_thin
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
import com.windscore.ui.SettingsDialog
import com.windscore.ui.WelcomeScreen
import com.windscore.ui.i18n.AppLanguage
import com.windscore.ui.i18n.AppStrings
import com.windscore.ui.i18n.strings
import com.windscore.ui.theme.WindScoreColors
import com.windscore.ui.theme.WindScoreTheme
import kotlinx.serialization.Serializable
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Serializable
enum class ViewMode { ROLL, SHEET_KEYBOARD, SHEET_FULL }

enum class LoopSetupState {
    IDLE,
    WAITING_FOR_START,
    WAITING_FOR_END,
    WAITING_FOR_NEW_START,
    WAITING_FOR_NEW_END
}

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
    var loopSetupState by remember { mutableStateOf(LoopSetupState.IDLE) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var appConfig by remember { mutableStateOf(com.windscore.core.loadAppConfig()) }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDarkTheme = when (appConfig.themeMode) {
        com.windscore.core.model.ThemeMode.FOLLOW_SYSTEM -> systemDark
        com.windscore.core.model.ThemeMode.LIGHT -> false
        com.windscore.core.model.ThemeMode.DARK -> true
    }
    val recentScores = remember { mutableStateListOf<Score>() }
    val tr = strings()

    var showSplash by remember { mutableStateOf(!isCompact) }
    LaunchedEffect(Unit) {
        delay(1000)
        showSplash = false
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2000)
            toastMessage = null
        }
    }

    var metronomeVol by remember { mutableStateOf(50) }
    var countdownEnabled by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }

    // 评分流程状态：scoringSession 非空表示正在评分；MIDI 事件会优先路由给它
    var scoringSession by remember { mutableStateOf<ScoreSession?>(null) }
    var scoringPhase by remember { mutableStateOf(com.windscore.ui.ScoringPhase.SETUP) }
    // 评分历史（当前乐谱）+ 历史对话框
    var scoreHistory by remember { mutableStateOf<List<com.windscore.core.model.ScoreRecord>>(emptyList()) }
    var showHistoryDialog by remember { mutableStateOf(false) }



    var activeLoopRange by remember(session) { mutableStateOf(session?.takeIf { it.isLoopActive }?.loopRange) }

    val synth = remember { createAudioSynth() }
    val scope = rememberCoroutineScope()

    fun changeLanguage(lang: AppLanguage) {
        AppStrings.update(lang)
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.windscore.core.saveAppLanguage(lang.code)
        }
    }
    val player = remember(currentScore) { 
        currentScore?.let { score -> 
            scope.launch(kotlinx.coroutines.Dispatchers.Default) {
                synth.start() // Pre-start/warm up the synthesizer in the background
            }
            ScorePlayer(score, synth, scope).apply {
                val tick = session?.currentTick ?: 0L
                if (tick > 0L) {
                    seekTo(tick)
                }
                loopRange = session?.takeIf { it.isLoopActive }?.loopRange
            }
        } 
    }
    val noTickFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(0L) }
    val noPlayFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
    val noPitchesFlow = remember { kotlinx.coroutines.flow.MutableStateFlow<Set<Int>>(emptySet()) }
    val playTick by (player?.tick ?: noTickFlow).collectAsState()
    val isPlaying by (player?.playing ?: noPlayFlow).collectAsState()
    val playingPitches by (player?.playingPitches ?: noPitchesFlow).collectAsState()

    LaunchedEffect(player, metronomeVol) {
        player?.metronomeVolume = metronomeVol
    }
    LaunchedEffect(player, playbackSpeed) {
        player?.speedMultiplier = playbackSpeed.toDouble()
    }

    // Auto-save ScoreSettings
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    LaunchedEffect(currentScore, practiceMode, viewMode, playbackSpeed, metronomeVol, countdownEnabled, pendingLoopStart, session, isPlaying) {
        val score = currentScore ?: return@LaunchedEffect
        val activeSession = session ?: return@LaunchedEffect
        snapshotFlow {
            if (!isPlaying) {
                com.windscore.core.model.ScoreSettings(
                    practiceMode = practiceMode,
                    viewMode = viewMode,
                    speedMultiplier = playbackSpeed.toDouble(),
                    loopStartTick = if (activeSession.isLoopActive) activeSession.loopRange.first else pendingLoopStart,
                    loopEndTick = if (activeSession.isLoopActive) activeSession.loopRange.last else null,
                    metronomeVol = metronomeVol,
                    countdownEnabled = countdownEnabled,
                    currentTick = activeSession.currentTick.let { if (it >= score.totalTicks) 0L else it }
                )
            } else null
        }
        .filterNotNull()
        .debounce(500L)
        .collect { settings ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.windscore.core.saveScoreSettings(score.id, settings)
            }
        }
    }

    // 自动清理 Session 协程
    DisposableEffect(session) {
        val currentSession = session
        onDispose {
            currentSession?.close()
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
                            (scoringSession ?: session)?.onNoteOn(ev.pitch)
                        }
                        is MidiEvent.NoteOff -> {
                            synth.noteOff(ev.pitch)
                            (scoringSession ?: session)?.onNoteOff(ev.pitch)
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
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val code = com.windscore.core.loadAppLanguage("zh")
            AppLanguage.fromCode(code)?.let { AppStrings.update(it) }
            com.windscore.core.loadRecentScores { loaded ->
                recentScores.clear()
                recentScores.addAll(loaded)
            }
        }
    }

    fun openScore(score: Score, restoreSettings: Boolean = true) {
        val scoreId = score.id.takeIf { it.isNotEmpty() } ?: score.rawXml?.let { com.windscore.core.calculateMd5(it) } ?: ""
        val xmlContent = if (score.rawXml == null && scoreId.isNotEmpty()) {
            com.windscore.core.loadScoreXml(scoreId)
        } else {
            score.rawXml
        }
        val finalScore = score.copy(id = scoreId, rawXml = xmlContent)

        if (currentScore?.id != finalScore.id) {
            renderMs = null
        }
        currentScore = finalScore

        if (restoreSettings) {
            val settings = com.windscore.core.loadScoreSettings(scoreId)
            practiceMode = settings.practiceMode
            viewMode = settings.viewMode
            playbackSpeed = settings.speedMultiplier.toFloat()
            metronomeVol = settings.metronomeVol
            countdownEnabled = settings.countdownEnabled
            pendingLoopStart = settings.loopStartTick

            session = ScoreSession(finalScore, synth, scope, settings.practiceMode).apply {
                if (settings.loopStartTick != null && settings.loopEndTick != null) {
                    setLoop(settings.loopStartTick, settings.loopEndTick, playAudio = false)
                    activeLoopRange = settings.loopStartTick..settings.loopEndTick
                }
                if (settings.currentTick > 0 && settings.currentTick < finalScore.totalTicks) {
                    seekTo(settings.currentTick)
                }
            }
        } else {
            val currentTickVal = session?.currentTick ?: 0L
            val prevLoopRange = session?.takeIf { it.isLoopActive }?.loopRange
            session = ScoreSession(finalScore, synth, scope, practiceMode).apply {
                if (prevLoopRange != null) {
                    setLoop(prevLoopRange.first, prevLoopRange.last, playAudio = false)
                    activeLoopRange = prevLoopRange
                }
                if (currentTickVal > 0) {
                    seekTo(currentTickVal)
                }
            }
        }

        val existingIndex = recentScores.indexOfFirst { it.title == score.title && it.totalTicks == score.totalTicks }
        if (existingIndex >= 0) {
            recentScores.removeAt(existingIndex)
        }
        recentScores.add(0, score.copy(id = scoreId, rawXml = null))
        if (recentScores.size > 10) recentScores.removeLast()
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.windscore.core.saveRecentScores(recentScores)
        }
        // 加载该乐谱的评分历史
        scoreHistory = emptyList()
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            scoreHistory = com.windscore.core.loadScoreHistory(scoreId)
        }
    }

    fun closeScore() {
        val score = currentScore
        val activeSession = session
        if (score != null && activeSession != null) {
            val settings = com.windscore.core.model.ScoreSettings(
                practiceMode = practiceMode,
                viewMode = viewMode,
                speedMultiplier = playbackSpeed.toDouble(),
                loopStartTick = if (activeSession.isLoopActive) activeSession.loopRange.first else pendingLoopStart,
                loopEndTick = if (activeSession.isLoopActive) activeSession.loopRange.last else null,
                metronomeVol = metronomeVol,
                countdownEnabled = countdownEnabled,
                currentTick = activeSession.currentTick.let { if (it >= score.totalTicks) 0L else it }
            )
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                com.windscore.core.saveScoreSettings(score.id, settings)
            }
        }
        player?.stop()
        scoringSession?.close()
        scoringSession = null
        scoringPhase = com.windscore.ui.ScoringPhase.SETUP
        session = null
        currentScore = null
    }

    fun resetSession() {
        session?.reset()
    }

    fun setPracticeMode(mode: PracticeMode) {
        practiceMode = mode
        currentScore?.let { openScore(it, restoreSettings = false) }
    }

    /** 开始评分：以指定手别创建独立的评分 Session（从头开始），并确保 MIDI 采集已开启。 */
    fun startScoring(mode: PracticeMode) {
        val score = currentScore ?: return
        player?.stop()
        scoringSession?.close()
        scoringSession = ScoreSession(score, synth, scope, mode)
        scoringPhase = com.windscore.ui.ScoringPhase.ACTIVE
        // 评分靠实时弹奏驱动，自动开启 MIDI 采集（若尚未开启）
        if (!running) running = true
    }

    /** 退出评分：释放评分 Session 并回到评分设置态。 */
    fun exitScoring() {
        scoringSession?.close()
        scoringSession = null
        scoringPhase = com.windscore.ui.ScoringPhase.SETUP
    }

    WindScoreTheme(darkTheme = isDarkTheme) {
        Box(Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = WindScoreColors.canvas,
            ) {
                Crossfade(
                targetState = currentScore,
                modifier = Modifier.fillMaxSize(),
                label = "welcomeScreenCrossfade"
            ) { score ->
                if (score == null) {
                    WelcomeScreen(
                        isCompact = isCompact,
                        recentScores = recentScores,
                        onScoreClick = { openScore(it) },
                        onRename = { scoreItem, newName ->
                            val idx = recentScores.indexOfFirst { it.title == scoreItem.title && it.totalTicks == scoreItem.totalTicks }
                            if (idx >= 0) {
                                recentScores[idx] = recentScores[idx].copy(title = newName)
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    com.windscore.core.saveRecentScores(recentScores)
                                }
                            }
                        },
                        onDelete = { scoreItem ->
                            recentScores.removeAll { it.title == scoreItem.title && it.totalTicks == scoreItem.totalTicks }
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.windscore.core.saveRecentScores(recentScores)
                            }
                        },
                        onLanguageChange = { changeLanguage(it) },
                        config = appConfig,
                        onConfigChange = {
                            appConfig = it
                            com.windscore.core.saveAppConfig(it)
                        }
                    )
                } else {
                    val s = session ?: return@Crossfade

                    // 播放中实时同步进度到练习 Session，并在暂停时做最终同步确保进度对齐
                    // 评分进行中时跳过：避免把练习 Session 进度重置，干扰评分主舞台
                    LaunchedEffect(isPlaying, playTick) {
                        if (scoringSession != null) return@LaunchedEffect
                        if (isPlaying) {
                            s.isHostedEnabled = false
                            s.seekTo(playTick)
                        } else {
                            s.isHostedEnabled = true
                            s.seekTo(playTick)
                        }
                    }

                    // 评分完成自动进入结算
                    LaunchedEffect(scoringSession?.finished) {
                        val sc = scoringSession
                        if (sc != null && sc.finished && scoringPhase == com.windscore.ui.ScoringPhase.ACTIVE) {
                            delay(1000)
                            // 持久化本次评分记录
                            val scoreId = s.score.id
                            val record = com.windscore.core.model.ScoreRecord(
                                timestamp = com.windscore.core.nowEpochMillis(),
                                percent = sc.scorePercent(),
                                cleanHits = sc.cleanHitCount(),
                                totalNotes = sc.totalActiveNotes(),
                                wrongPresses = sc.wrongPressCount(),
                                practiceMode = sc.practiceMode,
                            )
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                scoreHistory = com.windscore.core.appendScoreRecord(scoreId, record)
                            }
                            scoringPhase = com.windscore.ui.ScoringPhase.RESULT
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
                        countdownEnabled = countdownEnabled,
                        onCountdownEnabledChange = { countdownEnabled = it },
                        metronomeVol = metronomeVol,
                        onMetronomeVolChange = { metronomeVol = it },
                        playbackSpeed = playbackSpeed,
                        onPlaybackSpeedChange = { playbackSpeed = it },
                        onSeek = { tick ->
                            when (loopSetupState) {
                                LoopSetupState.WAITING_FOR_START -> {
                                    pendingLoopStart = tick
                                    loopSetupState = LoopSetupState.WAITING_FOR_END
                                    toastMessage = tr.loopStartAdded
                                }
                                LoopSetupState.WAITING_FOR_END -> {
                                    val start = pendingLoopStart ?: 0L
                                    s.setLoop(start, tick)
                                    val range = if (start < tick) start..tick else tick..start
                                    activeLoopRange = range
                                    player?.loopRange = range
                                    pendingLoopStart = null
                                    loopSetupState = LoopSetupState.IDLE
                                    toastMessage = tr.loopSetDone
                                    player?.seekTo(range.first)
                                    s.seekTo(range.first)
                                }
                                LoopSetupState.WAITING_FOR_NEW_START -> {
                                    val end = s.loopRange.last
                                    s.setLoop(tick, end)
                                    val range = if (tick < end) tick..end else end..tick
                                    activeLoopRange = range
                                    player?.loopRange = range
                                    loopSetupState = LoopSetupState.IDLE
                                    toastMessage = tr.settingDone
                                    player?.seekTo(range.first)
                                    s.seekTo(range.first)
                                }
                                LoopSetupState.WAITING_FOR_NEW_END -> {
                                    val start = s.loopRange.first
                                    s.setLoop(start, tick)
                                    val range = if (start < tick) start..tick else tick..start
                                    activeLoopRange = range
                                    player?.loopRange = range
                                    loopSetupState = LoopSetupState.IDLE
                                    toastMessage = tr.settingDone
                                    player?.seekTo(range.first)
                                    s.seekTo(range.first)
                                }
                                LoopSetupState.IDLE -> {
                                    if (activeLoopRange != null) {
                                        toastMessage = tr.closeLoopFirst
                                    } else {
                                        player?.seekTo(tick)
                                        s.seekTo(tick)
                                    }
                                }
                            }
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
                        onLoopStart = {
                            if (activeLoopRange != null) {
                                loopSetupState = LoopSetupState.WAITING_FOR_NEW_START
                                toastMessage = tr.clickNewStart
                            } else {
                                loopSetupState = LoopSetupState.WAITING_FOR_START
                                toastMessage = tr.clickStartPos
                            }
                        },
                        onLoopEnd = {
                            if (activeLoopRange != null) {
                                loopSetupState = LoopSetupState.WAITING_FOR_NEW_END
                                toastMessage = tr.clickNewEnd
                            }
                        },
                        onClearLoop = {
                            s.clearLoop()
                            activeLoopRange = null
                            player?.loopRange = null
                            pendingLoopStart = null
                            loopSetupState = LoopSetupState.IDLE
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
                        toastMessage = toastMessage,
                        activeLoopRange = activeLoopRange,
                        scoringSession = scoringSession,
                        scoringPhase = scoringPhase,
                        onStartScoring = { startScoring(it) },
                        onExitScoring = { exitScoring() },
                        scoreHistory = scoreHistory,
                        onShowHistory = { showHistoryDialog = true },
                        appConfig = appConfig,
                    )

                    SettingsDialog(
                        visible = showSettings,
                        onDismiss = { showSettings = false }
                    ) {
                        WindScoreTheme(darkTheme = isDarkTheme) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = WindScoreColors.canvas,
                            ) {
                                SettingsScreen(
                                    onBack = { showSettings = false },
                                    onLanguageChange = { changeLanguage(it) },
                                    config = appConfig,
                                    onConfigChange = {
                                        appConfig = it
                                        com.windscore.core.saveAppConfig(it)
                                    }
                                )
                            }
                        }
                    }

                    // 评分历史对话框
                    SettingsDialog(
                        visible = showHistoryDialog,
                        onDismiss = { showHistoryDialog = false },
                        title = tr.scoreHistory,
                    ) {
                        WindScoreTheme(darkTheme = isDarkTheme) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = WindScoreColors.canvas,
                            ) {
                                com.windscore.ui.ScoreHistoryContent(
                                    records = scoreHistory,
                                    onDismiss = { showHistoryDialog = false },
                                )
                            }
                        }
                    }
                }
            }
        }
        // Splash Screen overlay
        AnimatedVisibility(
            visible = showSplash,
                enter = fadeIn(),
                exit = fadeOut(animationSpec = tween(durationMillis = 400))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(WindScoreColors.canvas)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                        ) { showSplash = false },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.piano_keys_thin),
                        contentDescription = "Loading",
                        tint = WindScoreColors.ink,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
        }
    }
}
