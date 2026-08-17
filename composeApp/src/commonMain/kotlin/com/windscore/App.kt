package com.windscore

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import com.windscore.audio.createAudioSynth
import com.windscore.core.engine.PlaybackController
import com.windscore.core.engine.PracticeController
import com.windscore.core.engine.ScoringController
import com.windscore.core.ScoreLibraryController
import com.windscore.core.AppSettingsController
import com.windscore.core.OpenedScore
import com.windscore.core.AppDiagnostic
import com.windscore.core.DiagnosticController
import com.windscore.core.MidiDiagnosticProblem
import com.windscore.core.MidiInputController
import com.windscore.core.model.PracticeMode
import com.windscore.core.model.Score
import com.windscore.midi.MidiManager
import com.windscore.ui.PlayerScreen
import com.windscore.ui.SettingsScreen
import com.windscore.ui.SettingsDialog
import com.windscore.ui.WelcomeScreen
import com.windscore.ui.i18n.AppLanguage
import com.windscore.ui.i18n.strings
import com.windscore.ui.theme.WindScoreColors
import com.windscore.ui.theme.WindScoreTheme
import kotlinx.serialization.Serializable
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
    onDarkThemeChanged: (Boolean) -> Unit = {},
) {
    var currentScore by remember { mutableStateOf<Score?>(null) }
    var pendingOpenedScore by remember { mutableStateOf<OpenedScore?>(null) }
    var viewMode by remember { mutableStateOf(ViewMode.SHEET_FULL) }
    var renderMs by remember { mutableStateOf<Double?>(null) }
    var pendingLoopStart by remember { mutableStateOf<Long?>(null) }
    var loopSetupState by remember { mutableStateOf(LoopSetupState.IDLE) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val diagnostics = remember { DiagnosticController() }
    val settingsController = remember { AppSettingsController(scope, diagnostics = diagnostics) }
    val settingsState by settingsController.state.collectAsState()
    val appConfig = settingsState.config
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDarkTheme = when (appConfig.themeMode) {
        com.windscore.core.model.ThemeMode.FOLLOW_SYSTEM -> systemDark
        com.windscore.core.model.ThemeMode.LIGHT -> false
        com.windscore.core.model.ThemeMode.DARK -> true
    }
    SideEffect { onDarkThemeChanged(isDarkTheme) }
    val library = remember { ScoreLibraryController(scope, diagnostics = diagnostics) }
    val recentScores by library.recentScores.collectAsState()
    val openedScore by library.openedScore.collectAsState()
    val libraryOpening by library.opening.collectAsState()
    val openingScore = libraryOpening || pendingOpenedScore != null
    val libraryHistory by library.scoreHistory.collectAsState()
    val tr = strings()
    val currentStrings by rememberUpdatedState(tr)
    var diagnosticMessage by remember { mutableStateOf<String?>(null) }

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

    LaunchedEffect(diagnostics) {
        diagnostics.events.collect { diagnostic ->
            diagnosticMessage = when (diagnostic) {
                is AppDiagnostic.Import -> currentStrings.importFailed
                is AppDiagnostic.Library -> currentStrings.storageUnavailable
                is AppDiagnostic.Settings -> currentStrings.settingsUnavailable
                is AppDiagnostic.Midi -> when (diagnostic.problem) {
                    MidiDiagnosticProblem.DISCONNECTED -> currentStrings.midiDisconnected
                    else -> currentStrings.midiUnavailable
                }
            }
        }
    }
    LaunchedEffect(diagnosticMessage) {
        if (diagnosticMessage != null) {
            delay(3000)
            diagnosticMessage = null
        }
    }

    var countdownEnabled by remember { mutableStateOf(false) }

    // 评分历史（当前乐谱）+ 历史对话框
    var scoreHistory by remember { mutableStateOf<List<com.windscore.core.model.ScoreRecord>>(emptyList()) }
    var showHistoryDialog by remember { mutableStateOf(false) }



    val synth = remember { createAudioSynth() }
    val practice = remember { PracticeController(synth, scope) }
    val practiceState by practice.state.collectAsState()
    val session = practiceState.session
    val practiceMode = practiceState.mode
    val playback = remember { PlaybackController(synth, scope) }
    val playbackState by playback.state.collectAsState()
    val playTick = playbackState.tick
    val isPlaying = playbackState.isPlaying
    val playingPitches = playbackState.playingPitches
    val playbackSpeed = playbackState.speed
    val metronomeVol = playbackState.metronomeVolume
    val activeLoopRange = playbackState.loopRange
    val scoring = remember {
        ScoringController(
            synth = synth,
            scope = scope,
            now = { com.windscore.core.nowEpochMillis() },
            onRecord = library::appendHistory,
        )
    }
    val scoringState by scoring.state.collectAsState()
    val scoringSession = scoringState.session
    val scoringPhase = scoringState.phase

    // MIDI 事件仲裁：scoring 优先于 practice；新练习模型通过 register 接入
    val midiInput = remember { MidiInputController(midiManager, synth, scope, diagnostics) }
    midiInput.register(scoring)
    midiInput.register(practice)
    val running by midiInput.running.collectAsState()

    val currentPracticeTick = session?.currentTick ?: 0L
    val currentPracticeLoop = session?.takeIf { it.isLoopActive }?.loopRange
    LaunchedEffect(
        currentScore,
        practiceMode,
        viewMode,
        playbackSpeed,
        metronomeVol,
        countdownEnabled,
        pendingLoopStart,
        currentPracticeTick,
        currentPracticeLoop,
        isPlaying,
    ) {
        val score = currentScore ?: return@LaunchedEffect
        val activeSession = session ?: return@LaunchedEffect
        settingsController.scheduleScoreSave(
            scoreId = score.id,
            settings = createScoreSettings(
                score = score,
                practiceMode = practiceMode,
                viewMode = viewMode,
                playbackSpeed = playbackSpeed,
                metronomeVol = metronomeVol,
                countdownEnabled = countdownEnabled,
                pendingLoopStart = pendingLoopStart,
                currentTick = activeSession.currentTick,
                activeLoopRange = currentPracticeLoop,
            ),
            isPlaying = isPlaying,
        )
    }

    LaunchedEffect(settingsState.language) {
        com.windscore.ui.i18n.AppStrings.update(settingsState.language)
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

    DisposableEffect(Unit) {
        onDispose {
            library.close()
            playback.close()
            practice.close()
            scoring.close()
            settingsController.close()
            midiInput.close()
            midiManager.stop()
            synth.stop()
        }
    }
    LaunchedEffect(libraryHistory) {
        scoreHistory = libraryHistory
    }

    fun applyOpenedScore(score: Score, scoreSettings: com.windscore.core.model.ScoreSettings?) {
        val restoreSettings = scoreSettings != null
        val scoreId = score.id.takeIf { it.isNotEmpty() } ?: score.rawXml?.let { com.windscore.core.calculateMd5(it) } ?: ""
        val finalScore = score.copy(id = scoreId)

        if (currentScore?.id != finalScore.id) {
            renderMs = null
        }
        currentScore = finalScore

        if (restoreSettings) {
            val restored = checkNotNull(scoreSettings)
            viewMode = restored.viewMode
            countdownEnabled = restored.countdownEnabled
            pendingLoopStart = restored.loopStartTick
            loopSetupState = restoreLoopSetupState(restored)

            val restoredLoop = if (restored.loopStartTick != null && restored.loopEndTick != null) {
                restored.loopStartTick..restored.loopEndTick
            } else null
            practice.load(
                score = finalScore,
                mode = restored.practiceMode,
                currentTick = restored.currentTick.takeIf { it in 1 until finalScore.totalTicks } ?: 0L,
                loopRange = restoredLoop,
            )
        } else {
            // 无保存设置（首次打开）：从零开始，不继承上一乐谱 session 的位置与循环
            practice.load(finalScore, practiceMode, currentTick = 0L, loopRange = null)
        }

        val activeSession = checkNotNull(practice.state.value.session)
        playback.load(
            score = finalScore,
            session = activeSession,
            initialTick = activeSession.currentTick,
            loopRange = activeSession.takeIf { it.isLoopActive }?.loopRange,
            speed = scoreSettings?.speedMultiplier?.toFloat() ?: playbackState.speed,
            metronomeVolume = scoreSettings?.metronomeVol ?: playbackState.metronomeVolume,
        )

    }

    LaunchedEffect(openedScore) {
        val opened = openedScore ?: return@LaunchedEffect
        pendingOpenedScore = opened
        settingsController.activateScore(opened.score.id)
        library.consumeOpenedScore()
    }
    LaunchedEffect(pendingOpenedScore, settingsState.scoreSettings) {
        val opened = pendingOpenedScore ?: return@LaunchedEffect
        val restored = settingsState.scoreSettings ?: return@LaunchedEffect
        if (settingsState.activeScoreId != opened.score.id) return@LaunchedEffect
        applyOpenedScore(opened.score, restored)
        pendingOpenedScore = null
        settingsController.consumeScoreSettings()
    }

    fun openScore(score: Score, restoreSettings: Boolean = true) {
        if (!restoreSettings) {
            applyOpenedScore(score, scoreSettings = null)
            return
        }

        library.open(score)
    }

    fun closeScore() {
        library.cancelOpening()
        library.clearActiveScore()
        val score = currentScore
        val activeSession = session
        if (score != null && activeSession != null) {
            val settings = createScoreSettings(
                score = score,
                practiceMode = practiceMode,
                viewMode = viewMode,
                playbackSpeed = playbackSpeed,
                metronomeVol = metronomeVol,
                countdownEnabled = countdownEnabled,
                pendingLoopStart = pendingLoopStart,
                currentTick = activeSession.currentTick,
                activeLoopRange = activeSession.takeIf { it.isLoopActive }?.loopRange,
            )
            settingsController.saveScoreNow(score.id, settings)
        }
        settingsController.clearActiveScore()
        playback.close()
        practice.close()
        scoring.exit()
        currentScore = null
    }

    fun resetSession() {
        playback.reset()
    }

    fun setPracticeMode(mode: PracticeMode) {
        val newSession = practice.changeMode(mode) ?: return
        playback.attachSession(newSession)
    }

    /** 开始评分：以指定手别创建独立的评分 Session（从头开始），并确保 MIDI 采集已开启。 */
    fun startScoring(mode: PracticeMode) {
        val score = currentScore ?: return
        playback.stop()
        playback.setPracticeSyncEnabled(false)
        scoring.start(score, mode)
        // 评分靠实时弹奏驱动，自动开启 MIDI 采集（若尚未开启）
        midiInput.setRunning(true)
    }

    /** 退出评分：释放评分 Session 并回到评分设置态。 */
    fun exitScoring() {
        scoring.exit()
        playback.setPracticeSyncEnabled(true)
    }

    // 系统返回键/手势：优先关弹窗 → 回到欢迎页 → 双击返回退出
    var backPressedTime by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    PlatformBackHandler {
        when {
            openingScore -> {
                library.cancelOpening()
                pendingOpenedScore = null
                settingsController.clearActiveScore()
            }
            showHistoryDialog -> showHistoryDialog = false
            showSettings -> showSettings = false
            currentScore != null -> closeScore()
            else -> {
                val now = com.windscore.core.nowEpochMillis()
                if (now - backPressedTime < 2000) {
                    exitApplication()
                } else {
                    backPressedTime = now
                    showPlatformToast(tr.pressAgainToExit)
                }
            }
        }
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
                        onImportFailure = { diagnostics.report(AppDiagnostic.Import(it)) },
                        onRename = { scoreItem, newName -> library.rename(scoreItem, newName) },
                        onDelete = { scoreItem -> library.delete(scoreItem) },
                        onLanguageChange = { settingsController.updateLanguage(it) },
                        config = appConfig,
                        onConfigChange = settingsController::updateConfig,
                    )
                } else {
                    val s = session ?: return@Crossfade

                    PlayerScreen(
                        score = s.score,
                        session = s,
                        isCompact = isCompact,
                        running = running,
                        practiceMode = practiceMode,
                        viewMode = viewMode,
                        pendingLoopStart = pendingLoopStart,
                        playTick = playTick,
                        isPlaying = isPlaying,
                        playingPitches = playingPitches,
                        renderMs = renderMs,
                        spacePressed = spacePressed,
                        countdownEnabled = countdownEnabled,
                        onCountdownEnabledChange = { countdownEnabled = it },
                        metronomeVol = metronomeVol,
                        onMetronomeVolChange = {
                            playback.setMetronomeVolume(it)
                        },
                        playbackSpeed = playbackSpeed,
                        onPlaybackSpeedChange = {
                            playback.setSpeed(it)
                        },
                        onSeek = { tick ->
                            when (loopSetupState) {
                                LoopSetupState.WAITING_FOR_START -> {
                                    pendingLoopStart = tick
                                    loopSetupState = LoopSetupState.WAITING_FOR_END
                                    toastMessage = tr.loopStartAdded
                                }
                                LoopSetupState.WAITING_FOR_END -> {
                                    val start = pendingLoopStart ?: 0L
                                    val range = if (start < tick) start..tick else tick..start
                                    pendingLoopStart = null
                                    loopSetupState = LoopSetupState.IDLE
                                    toastMessage = tr.loopSetDone
                                    playback.setLoop(range.first, range.last)
                                }
                                LoopSetupState.WAITING_FOR_NEW_START -> {
                                    val end = s.loopRange.last
                                    val range = if (tick < end) tick..end else end..tick
                                    loopSetupState = LoopSetupState.IDLE
                                    toastMessage = tr.settingDone
                                    playback.setLoop(range.first, range.last)
                                }
                                LoopSetupState.WAITING_FOR_NEW_END -> {
                                    val start = s.loopRange.first
                                    val range = if (start < tick) start..tick else tick..start
                                    loopSetupState = LoopSetupState.IDLE
                                    toastMessage = tr.settingDone
                                    playback.setLoop(range.first, range.last)
                                }
                                LoopSetupState.IDLE -> {
                                    if (activeLoopRange != null) {
                                        toastMessage = tr.closeLoopFirst
                                    } else {
                                        playback.seekTo(tick)
                                    }
                                }
                            }
                        },
                        onStepForward = {
                            playback.stepForward()
                        },
                        onStepBackward = {
                            playback.stepBackward()
                        },
                        onBack = { closeScore() },
                        onToggleMidi = {
                            midiInput.toggle()
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
                            playback.clearLoop()
                            pendingLoopStart = null
                            loopSetupState = LoopSetupState.IDLE
                        },
                        onTogglePlay = {
                            playback.toggle()
                        },
                        onStopPlay = { playback.stop() },
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
                        onDismiss = { showSettings = false },
                        title = tr.settings,
                        darkTheme = isDarkTheme,
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = WindScoreColors.canvas,
                        ) {
                            SettingsScreen(
                                onBack = { showSettings = false },
                                onLanguageChange = { settingsController.updateLanguage(it) },
                                config = appConfig,
                                onConfigChange = settingsController::updateConfig,
                            )
                        }
                    }

                    // 评分历史对话框
                    SettingsDialog(
                        visible = showHistoryDialog,
                        onDismiss = { showHistoryDialog = false },
                        title = tr.scoreHistory,
                        darkTheme = isDarkTheme,
                    ) {
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
        if (openingScore) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(WindScoreColors.canvas.copy(alpha = 0.92f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) {},
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = WindScoreColors.ink)
                    Text(
                        text = tr.loadingScore,
                        color = WindScoreColors.ink,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = diagnosticMessage != null,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 32.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    color = WindScoreColors.ink,
                    contentColor = WindScoreColors.canvas,
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        text = diagnosticMessage.orEmpty(),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
