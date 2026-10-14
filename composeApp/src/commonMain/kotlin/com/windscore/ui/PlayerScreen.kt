package com.windscore.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberCoroutineScope
import windscore.composeapp.generated.resources.clock_thin
import windscore.composeapp.generated.resources.corners_out_thin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.windscore.core.engine.ScoreSession
import com.windscore.core.engine.ScoringPhase
import com.windscore.core.traceState
import com.windscore.core.model.PracticeMode
import com.windscore.core.model.Score
import com.windscore.ViewMode
import com.windscore.ui.components.PillButton
import com.windscore.ui.components.PillButtonStyle
import com.windscore.ui.components.CustomSwitch
import com.windscore.ui.components.PillToggle
import com.windscore.ui.components.CircularIconButton
import com.windscore.ui.components.pitchName
import com.windscore.ui.i18n.strings
import com.windscore.ui.theme.windScoreColors
import com.windscore.ui.theme.WindScoreRounded
import com.windscore.ui.theme.WindScoreSpacing
import org.jetbrains.compose.resources.painterResource
import windscore.composeapp.generated.resources.Res
import windscore.composeapp.generated.resources.arrow_left_thin
import windscore.composeapp.generated.resources.arrows_counter_clockwise_thin
import windscore.composeapp.generated.resources.gear_six_thin
import windscore.composeapp.generated.resources.piano_keys_thin
import windscore.composeapp.generated.resources.book_open_text_thin
import windscore.composeapp.generated.resources.music_notes_thin
import windscore.composeapp.generated.resources.pause_thin
import windscore.composeapp.generated.resources.play_thin
import windscore.composeapp.generated.resources.speaker_high_thin
import windscore.composeapp.generated.resources.speaker_slash_thin
import windscore.composeapp.generated.resources.stop_thin
import windscore.composeapp.generated.resources.repeat_thin
import windscore.composeapp.generated.resources.x_thin
import windscore.composeapp.generated.resources.pencil_simple_thin
/** 双指唤出/隐藏控件面板的滑动阈值（像素）。 */
private const val PANEL_SWIPE_THRESHOLD = 48f

enum class SidebarTab { PRACTICE, SCORING }

@Composable
fun PlayerScreen(
    score: Score,
    session: ScoreSession,
    isCompact: Boolean,
    running: Boolean,
    practiceMode: PracticeMode,
    viewMode: ViewMode,
    pendingLoopStart: Long?,
    playTick: Long,
    isPlaying: Boolean,
    playingPitches: Set<Int>,
    renderMs: Double?,
    spacePressed: Int = 0,
    countdownEnabled: Boolean,
    onCountdownEnabledChange: (Boolean) -> Unit,
    metronomeVol: Int,
    onMetronomeVolChange: (Int) -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
    onSeek: (Long) -> Unit,
    onStepForward: () -> Unit,
    onStepBackward: () -> Unit,
    onBack: () -> Unit,
    onToggleMidi: () -> Unit,
    onReset: () -> Unit,
    onModeChange: (PracticeMode) -> Unit,
    onViewChange: (ViewMode) -> Unit,
    onLoopStart: () -> Unit,
    onLoopEnd: () -> Unit,
    onClearLoop: () -> Unit,
    onTogglePlay: () -> Unit,
    onStopPlay: () -> Unit,
    onRenderTime: (Double) -> Unit,
    onShowSettings: () -> Unit = {},
    toastMessage: String? = null,
    activeLoopRange: LongRange? = null,
    scoringSession: ScoreSession? = null,
    scoringPhase: ScoringPhase = ScoringPhase.SETUP,
    onStartScoring: (PracticeMode) -> Unit = {},
    onExitScoring: () -> Unit = {},
    scoreHistory: List<com.windscore.core.model.ScoreRecord> = emptyList(),
    onShowHistory: () -> Unit = {},
    onShowAttemptDiagnostics: () -> Unit = {},
    appConfig: com.windscore.core.model.AppConfig = com.windscore.core.loadAppConfig(),
) {
    val scope = rememberCoroutineScope()
    val s = strings()
    var countdownSeconds by remember { mutableStateOf(0) }
    var countdownJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var metronomePrevVol by remember { mutableStateOf(50) } // 记住静音前音量
    var sidebarTab by remember { mutableStateOf(SidebarTab.PRACTICE) }
    var localToastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(localToastMessage) {
        if (localToastMessage != null) {
            delay(2000)
            localToastMessage = null
        }
    }

    fun toggleMute() {
        if (metronomeVol > 0) {
            metronomePrevVol = metronomeVol
            onMetronomeVolChange(0)
        } else {
            onMetronomeVolChange(if (metronomePrevVol > 0) metronomePrevVol else 50)
        }
    }

    val sheetReady = viewMode == ViewMode.ROLL || renderMs != null

    // 评分进行中时主舞台展示评分 Session；否则展示练习 Session
    val stageSession = scoringSession ?: session
    val scoringActive = scoringPhase != ScoringPhase.SETUP

    // Android(compact)：双指下滑唤出控件面板，双指上滑隐藏。
    // 单指交给内容（移动乐谱/点击光标）；弹琴(MIDI)不影响。
    var controlsVisible by remember { mutableStateOf(true) }
    val panelGestureModifier = if (isCompact) {
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                var startY: Float? = null
                var armed = false // 一次双指手势只触发一次，避免持续滑动反复切换
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val active = event.changes.filter { it.pressed }
                    if (active.size >= 2) {
                        // 两指：消费事件，避免 NativeSheetView 的单指拖拽响应
                        active.forEach { it.consume() }
                        if (!armed) {
                            val avgY = active.map { it.position.y }.average().toFloat()
                            if (startY == null) startY = avgY
                            val s = startY
                            if (kotlin.math.abs(avgY - s) >= PANEL_SWIPE_THRESHOLD) {
                                controlsVisible = avgY - s > 0 // 下滑(dy>0)→显示；上滑→隐藏
                                armed = true
                                startY = null
                            }
                        }
                    } else {
                        startY = null
                        armed = false
                    }
                }
            }
        }
    } else Modifier

    // 双指手势提示：进入乐谱页提示"上滑收起"；收起后提示"下滑显示"（Android 用系统 Toast）
    if (isCompact) {
        LaunchedEffect(Unit) { com.windscore.showPlatformToast(s.swipeUpToCollapse) }
        LaunchedEffect(controlsVisible) {
            if (!controlsVisible) com.windscore.showPlatformToast(s.swipeDownToExpand)
        }
    }

    var lastHandledSpacePressed by remember { mutableStateOf(spacePressed) }

    LaunchedEffect(spacePressed) {
        if (spacePressed > lastHandledSpacePressed && sheetReady) {
            lastHandledSpacePressed = spacePressed
            if (scoringActive) {
                // 评分进行中/结算：忽略空格（评分靠实时弹奏，不触发 ScorePlayer）
            } else if (sidebarTab == SidebarTab.SCORING) {
                localToastMessage = s.scoringNoPlayToast
            } else {
                if (!isPlaying && countdownEnabled) {
                    if (countdownSeconds == 0) {
                        countdownJob?.cancel()
                        countdownJob = scope.launch {
                            countdownSeconds = appConfig.defaultCountdownSeconds.coerceAtLeast(1)
                            while (countdownSeconds > 0) {
                                delay(1000)
                                countdownSeconds--
                            }
                            onTogglePlay()
                        }
                    } else {
                        countdownJob?.cancel()
                        countdownSeconds = 0
                    }
                } else {
                    onTogglePlay()
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().then(panelGestureModifier)) {
        Column(Modifier.fillMaxSize()) {
            if (isCompact) {
                // 控件淡入/淡出 + 折叠，隐藏后让乐谱/卷帘占满屏幕
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { -it },
                    exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { -it },
                ) {
                    Column {
                        PlayerToolbar(
                            title = score.title.ifBlank { s.untitledScore },
                            running = running,
                            isCompact = isCompact,
                            onBack = {
                                countdownJob?.cancel()
                                countdownSeconds = 0
                                onBack()
                            },
                            onToggleMidi = onToggleMidi,
                            onReset = onReset,
                            onShowSettings = onShowSettings,
                        )
                        PlayerControlBar(
                            session = session,
                            practiceMode = practiceMode,
                            viewMode = viewMode,
                            pendingLoopStart = pendingLoopStart,
                            scoreHasSheet = score.supportsSheetView,
                            isCompact = isCompact,
                            isPlaying = isPlaying,
                            sheetReady = sheetReady,
                            onModeChange = onModeChange,
                            onViewChange = onViewChange,
                            onLoopStart = onLoopStart,
                            onLoopEnd = onLoopEnd,
                            onClearLoop = onClearLoop,
                            onTogglePlay = onTogglePlay,
                            onStopPlay = onStopPlay,
                            activeLoopRange = activeLoopRange,
                        )
                    }
                }
                MainStage(
                    session = stageSession,
                    viewMode = viewMode,
                    playTick = playTick,
                    isPlaying = isPlaying,
                    playingPitches = playingPitches,
                    pendingLoopStart = pendingLoopStart,
                    activeLoopRange = activeLoopRange,
                    onSeek = if (scoringActive) ({ }) else onSeek,
                    onStepForward = if (scoringActive) ({ }) else onStepForward,
                    onStepBackward = if (scoringActive) ({ }) else onStepBackward,
                    renderMs = renderMs,
                    isCompact = isCompact,
                    onRenderTime = onRenderTime,
                    scoringPhase = scoringPhase,
                    onReturnToPractice = {
                        onExitScoring()
                        sidebarTab = SidebarTab.PRACTICE
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                PlayerToolbar(
                    title = score.title.ifBlank { s.untitledScore },
                    running = running,
                    isCompact = isCompact,
                    onBack = {
                        countdownJob?.cancel()
                        countdownSeconds = 0
                        onBack()
                    },
                    onToggleMidi = onToggleMidi,
                    onReset = onReset,
                    onShowSettings = onShowSettings,
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 12.dp, start = 12.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.md)
                ) {
                    MainStage(
                        session = stageSession,
                        viewMode = viewMode,
                        playTick = playTick,
                        isPlaying = isPlaying,
                        playingPitches = playingPitches,
                        pendingLoopStart = pendingLoopStart,
                        activeLoopRange = activeLoopRange,
                        onSeek = if (scoringActive) ({ }) else onSeek,
                        onStepForward = if (scoringActive) ({ }) else onStepForward,
                        onStepBackward = if (scoringActive) ({ }) else onStepBackward,
                        renderMs = renderMs,
                        isCompact = isCompact,
                        onRenderTime = onRenderTime,
                        scoringPhase = scoringPhase,
                        onReturnToPractice = {
                            onExitScoring()
                            sidebarTab = SidebarTab.PRACTICE
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    Column(
                        modifier = Modifier
                            .width(300.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.md)
                    ) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            Crossfade(targetState = sidebarTab) { tab ->
                                when (tab) {
                                    SidebarTab.PRACTICE -> {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.md)
                                        ) {
                                            StatusCard(session = session)
                                            ControlSidebar(
                                                session = session,
                                                practiceMode = practiceMode,
                                                viewMode = viewMode,
                                                pendingLoopStart = pendingLoopStart,
                                                scoreHasSheet = score.supportsSheetView,
                                                isPlaying = isPlaying,
                                                sheetReady = sheetReady,
                                                running = running,
                                                playbackSpeed = playbackSpeed,
                                                onPlaybackSpeedChange = onPlaybackSpeedChange,
                                                onToggleMidi = onToggleMidi,
                                                countdownEnabled = countdownEnabled,
                                                onCountdownEnabledChange = onCountdownEnabledChange,
                                                metronomeVol = metronomeVol,
                                                onMetronomeVolChange = onMetronomeVolChange,
                                                onToggleMute = ::toggleMute,
                                                onModeChange = onModeChange,
                                                onViewChange = onViewChange,
                                                onLoopStart = onLoopStart,
                                                onLoopEnd = onLoopEnd,
                                                onClearLoop = onClearLoop,
                                                activeLoopRange = activeLoopRange,
                                                onTogglePlay = {
                                                    if (!isPlaying && countdownEnabled) {
                                                        if (countdownSeconds == 0) {
                                                            countdownJob?.cancel()
                                                            countdownJob = scope.launch {
                                                                countdownSeconds = appConfig.defaultCountdownSeconds.coerceAtLeast(1)
                                                                while (countdownSeconds > 0) {
                                                                    delay(1000)
                                                                    countdownSeconds--
                                                                }
                                                                onTogglePlay()
                                                            }
                                                        } else {
                                                            countdownJob?.cancel()
                                                            countdownSeconds = 0
                                                        }
                                                    } else {
                                                        onTogglePlay()
                                                    }
                                                },
                                                onStopPlay = {
                                                    countdownJob?.cancel()
                                                    countdownSeconds = 0
                                                    onStopPlay()
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    SidebarTab.SCORING -> {
                                        Crossfade(targetState = scoringPhase) { phase ->
                                            when (phase) {
                                                ScoringPhase.SETUP -> ScoringSetupPanel(
                                                    scoreHasSheet = score.supportsSheetView,
                                                    viewMode = viewMode,
                                                    onViewChange = onViewChange,
                                                    onStartScoring = onStartScoring,
                                                    history = scoreHistory,
                                                    onShowHistory = onShowHistory,
                                                    onShowAttemptDiagnostics = onShowAttemptDiagnostics,
                                                )
                                                ScoringPhase.ACTIVE -> ScoringActivePanel(
                                                    session = scoringSession ?: session,
                                                    onExit = onExitScoring,
                                                )
                                                ScoringPhase.RESULT -> ScoringDonePanel()
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.sm)
                        ) {
                            PillButton(
                                onClick = { sidebarTab = SidebarTab.PRACTICE },
                                enabled = !scoringActive,
                                style = if (sidebarTab == SidebarTab.PRACTICE) PillButtonStyle.PRIMARY else PillButtonStyle.SECONDARY,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.music_notes_thin),
                                        contentDescription = s.practice,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(s.practice)
                                }
                            }
                            PillButton(
                                onClick = { sidebarTab = SidebarTab.SCORING },
                                enabled = !scoringActive,
                                style = if (sidebarTab == SidebarTab.SCORING) PillButtonStyle.PRIMARY else PillButtonStyle.SECONDARY,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.pencil_simple_thin),
                                        contentDescription = s.scoring,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(s.scoring)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 常驻展开把手：控件收起后顶部保留可见入口（可发现性，替代仅一次的 Toast 提示）
        AnimatedVisibility(
            visible = isCompact && !controlsVisible,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            CollapsedPanelHandle(onClick = { controlsVisible = true })
        }

        val activeToast = toastMessage ?: localToastMessage
        AnimatedVisibility(
            visible = activeToast != null,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(WindScoreRounded.md),
                    color = windScoreColors().ink,
                    contentColor = Color.White,
                    shadowElevation = 4.dp,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = activeToast ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        if (countdownSeconds > 0) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(WindScoreRounded.lg))
                        .background(Color.Black.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$countdownSeconds",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 48.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerToolbar(
    title: String,
    running: Boolean,
    isCompact: Boolean,
    onBack: () -> Unit,
    onToggleMidi: () -> Unit,
    onReset: () -> Unit,
    onShowSettings: () -> Unit,
) {
    val s = strings()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = WindScoreSpacing.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularIconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(Res.drawable.arrow_left_thin),
                    contentDescription = s.back,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(WindScoreSpacing.sm))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W500),
                color = windScoreColors().ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.sm)) {
            if (isCompact) {
                CircularIconButton(onClick = onReset) {
                    Icon(
                        painter = painterResource(Res.drawable.arrows_counter_clockwise_thin),
                        contentDescription = s.reset,
                        modifier = Modifier.size(20.dp),
                    )
                }
                PillButton(style = if (running) PillButtonStyle.SECONDARY else PillButtonStyle.PRIMARY, onClick = onToggleMidi) {
                    Text(if (running) s.stopMidi else s.startMidi)
                }
            }
            CircularIconButton(onClick = onShowSettings) {
                Icon(
                    painter = painterResource(Res.drawable.gear_six_thin),
                    contentDescription = s.settings,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun CollapsedPanelHandle(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = strings()
    val handleShape = RoundedCornerShape(
        bottomStart = WindScoreRounded.lg,
        bottomEnd = WindScoreRounded.lg,
    )
    Box(
        modifier = modifier
            .size(width = 72.dp, height = 40.dp)
            .clip(handleShape)
            .background(windScoreColors().canvas.copy(alpha = 0.92f))
            .border(1.dp, windScoreColors().hairline, handleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.corners_out_thin),
            contentDescription = s.swipeDownToExpand,
            tint = windScoreColors().ink.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun PlayerControlBar(
    session: ScoreSession,
    practiceMode: PracticeMode,
    viewMode: ViewMode,
    pendingLoopStart: Long?,
    scoreHasSheet: Boolean,
    isCompact: Boolean,
    isPlaying: Boolean,
    sheetReady: Boolean,
    onModeChange: (PracticeMode) -> Unit,
    onViewChange: (ViewMode) -> Unit,
    onLoopStart: () -> Unit,
    onLoopEnd: () -> Unit,
    onClearLoop: () -> Unit,
    onTogglePlay: () -> Unit,
    onStopPlay: () -> Unit,
    activeLoopRange: LongRange? = null,
) {
    val s = strings()
    val ss = session.state.collectAsState().value
    val labelColor = windScoreColors().ink.copy(alpha = 0.5f)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = WindScoreSpacing.lg, vertical = WindScoreSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs),
    ) {
        // 一行带标题的按钮组：每组标题直接对齐到自己的按钮，所有按钮共享同一行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            // 模式
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(s.mode, style = MaterialTheme.typography.labelSmall, color = labelColor)
                Row(horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)) {
                    PillToggle(selected = practiceMode == PracticeMode.BOTH, onClick = { onModeChange(PracticeMode.BOTH) }, label = s.bothHands)
                    PillToggle(selected = practiceMode == PracticeMode.RIGHT_ONLY, onClick = { onModeChange(PracticeMode.RIGHT_ONLY) }, label = s.rightHand)
                    PillToggle(selected = practiceMode == PracticeMode.LEFT_ONLY, onClick = { onModeChange(PracticeMode.LEFT_ONLY) }, label = s.leftHand)
                }
            }
            // 视图
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(s.view, style = MaterialTheme.typography.labelSmall, color = labelColor)
                Row(horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)) {
                    CircularIconButton(onClick = { onViewChange(ViewMode.ROLL) }, selected = viewMode == ViewMode.ROLL) {
                        Icon(painterResource(Res.drawable.piano_keys_thin), s.pianoRoll, Modifier.size(20.dp))
                    }
                    if (scoreHasSheet) {
                        CircularIconButton(onClick = { onViewChange(ViewMode.SHEET_KEYBOARD) }, selected = viewMode == ViewMode.SHEET_KEYBOARD) {
                            Icon(painterResource(Res.drawable.book_open_text_thin), s.readSheet, Modifier.size(20.dp))
                        }
                        CircularIconButton(onClick = { onViewChange(ViewMode.SHEET_FULL) }, selected = viewMode == ViewMode.SHEET_FULL) {
                            Icon(painterResource(Res.drawable.music_notes_thin), s.viewSheet, Modifier.size(20.dp))
                        }
                    }
                }
            }
            // 播放
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(s.playback, style = MaterialTheme.typography.labelSmall, color = labelColor)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)) {
                    CircularIconButton(onClick = onTogglePlay, enabled = sheetReady) {
                        Icon(painterResource(if (isPlaying) Res.drawable.pause_thin else Res.drawable.play_thin), if (isPlaying) s.pause else s.play, Modifier.size(20.dp))
                    }
                    CircularIconButton(onClick = onStopPlay, enabled = sheetReady) {
                        Icon(painterResource(Res.drawable.stop_thin), s.stop, Modifier.size(20.dp))
                    }
                    Text("${(ss.currentTick * 100 / (session.score.totalTicks.coerceAtLeast(1))).coerceIn(0, 100)}%", style = MaterialTheme.typography.bodySmall, color = windScoreColors().ink)
                }
            }
            // 循环
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(s.loop, style = MaterialTheme.typography.labelSmall, color = labelColor)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)) {
                    CircularIconButton(onClick = onLoopStart, enabled = activeLoopRange == null) {
                        Icon(painterResource(Res.drawable.repeat_thin), s.setLoopStart, Modifier.size(20.dp))
                    }
                    CircularIconButton(onClick = onLoopEnd, enabled = activeLoopRange == null && pendingLoopStart != null) {
                        Icon(painterResource(Res.drawable.repeat_thin), s.setLoopEnd, Modifier.size(20.dp))
                    }
                    CircularIconButton(onClick = onClearLoop, enabled = activeLoopRange != null) {
                        Icon(painterResource(Res.drawable.x_thin), s.clearLoop, Modifier.size(20.dp))
                    }
                }
            }
        }
        // 状态行
        val pending = ss.pendingPitches.joinToString { pitchName(it) }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.md)) {
            Text(
                text = if (ss.finished) s.finishedAccuracy(session.scorePercent()) else s.toPlayPending(pending),
                style = MaterialTheme.typography.bodySmall,
                color = if (ss.finished) windScoreColors().semanticSuccess else windScoreColors().ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (activeLoopRange != null) {
                Text(s.loopRangeLabel(activeLoopRange.first, activeLoopRange.last), style = MaterialTheme.typography.bodySmall, color = windScoreColors().accentMagenta, maxLines = 1)
            }
        }
    }
}

@Composable
private fun StatusLine(session: ScoreSession, viewMode: ViewMode = ViewMode.ROLL, sheetReady: Boolean = true) {
    val s = strings()
    val ss = session.state.collectAsState().value
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val pending = ss.pendingPitches.joinToString { pitchName(it) }
        Text(
            if (ss.finished) s.finishedAccuracy(session.scorePercent()) else s.toPlayPending(pending),
            style = MaterialTheme.typography.bodySmall,
            color = if (ss.finished) windScoreColors().semanticSuccess else windScoreColors().ink,
        )
        Text(
            s.progressTick(ss.progress, ss.groupCount, ss.currentTick, session.score.totalTicks),
            style = MaterialTheme.typography.bodySmall,
            color = windScoreColors().ink,
        )
    }
}

@Composable
private fun MainStage(
    session: ScoreSession,
    viewMode: ViewMode,
    playTick: Long,
    isPlaying: Boolean,
    playingPitches: Set<Int>,
    pendingLoopStart: Long?,
    activeLoopRange: LongRange?,
    onSeek: (Long) -> Unit,
    onStepForward: () -> Unit,
    onStepBackward: () -> Unit,
    renderMs: Double?,
    isCompact: Boolean,
    onRenderTime: (Double) -> Unit,
    scoringPhase: ScoringPhase = ScoringPhase.SETUP,
    onReturnToPractice: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val ss = session.state.collectAsState().value
    val frameH = if (isCompact) WindScoreSpacing.sm else 0.dp
    val frameV = if (isCompact) WindScoreSpacing.sm else 0.dp
    val innerPadding = if (isCompact) WindScoreSpacing.lg else WindScoreSpacing.sm
    val tick = if (isPlaying) playTick else ss.currentTick
    val showRoll = viewMode == ViewMode.ROLL
    val showSheetKb = viewMode == ViewMode.SHEET_KEYBOARD
    val showSheetFull = viewMode == ViewMode.SHEET_FULL
    val sheetVisible = showSheetKb || showSheetFull

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = frameH, vertical = frameV)
            .clip(RoundedCornerShape(WindScoreRounded.lg))
            .background(windScoreColors().blockLime),
    ) {
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            val rawXml = session.score.rawXml
            val activePitches = if (isPlaying) playingPitches else ss.heldPitches
            val targetPitches = if (isPlaying) playingPitches else ss.cuePitches
            val hostedTargetPitches = if (isPlaying) emptySet() else ss.hostedCuePitches

            LaunchedEffect(
                session.traceId,
                viewMode,
                tick,
                targetPitches,
                hostedTargetPitches,
                activePitches,
            ) {
                if (viewMode == ViewMode.SHEET_KEYBOARD) {
                    traceState(
                        "ui.sheetKeyboard session=${session.traceId} tick=$tick playing=$isPlaying " +
                            "cue=${targetPitches.sorted()} hostedCue=${hostedTargetPitches.sorted()} " +
                            "held=${activePitches.sorted()}",
                    )
                }
            }

            var sheetSnapshot by remember(rawXml) { mutableStateOf<SheetSnapshot?>(null) }

            // WebView 渲染器始终在后台静默运行
            if (rawXml != null) {
                val sheetFraction = if (showSheetKb) 0.72f else 1f
                ScoreSheetView(
                    rawXml = rawXml,
                    currentTickProvider = { tick },
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(sheetFraction)
                        .offset(x = 99999.dp),
                    divisions = session.score.divisions,
                    onRenderTime = onRenderTime,
                    onSnapshot = { snap -> sheetSnapshot = snap },
                )
            }

            Crossfade(
                targetState = viewMode,
                modifier = Modifier.fillMaxSize(),
                label = "mainStageCrossfade"
            ) { mode ->
                when (mode) {
                    ViewMode.ROLL -> {
                        PianoRoll(
                            score = session.score,
                            currentTickProvider = { tick },
                            modifier = Modifier.fillMaxSize()
                                .clip(RoundedCornerShape(WindScoreRounded.lg)),
                            hostedTrack = session.hostedTrack,
                            heldPitchesProvider = { activePitches },
                            hitNoteIdsProvider = { ss.hitNoteIds },
                            pulseIntensityProvider = { ss.pulseIntensity },
                            bpmProvider = { session.score.tempo.takeIf { it > 0 } ?: 120 },
                            pendingLoopStart = pendingLoopStart,
                            loopRange = activeLoopRange,
                        )
                    }
                    ViewMode.SHEET_KEYBOARD, ViewMode.SHEET_FULL -> {
                        if (rawXml != null) {
                            val isKb = mode == ViewMode.SHEET_KEYBOARD
                            val sheetFraction = if (isKb) 0.72f else 1f
                            var loadingSeconds by remember(rawXml) { mutableStateOf(0) }
                            LaunchedEffect(sheetSnapshot, rawXml) {
                                if (sheetSnapshot == null) {
                                    loadingSeconds = 0
                                    while (true) {
                                        delay(1000)
                                        loadingSeconds++
                                    }
                                }
                            }

                            if (sheetSnapshot == null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(sheetFraction)
                                        .clip(RoundedCornerShape(WindScoreRounded.lg))
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(
                                            color = windScoreColors().accentMagenta,
                                            strokeWidth = 3.dp,
                                            modifier = Modifier.size(36.dp),
                                        )
                                        Spacer(Modifier.height(WindScoreSpacing.md))
                                        val loadingText = when {
                                            loadingSeconds < 10 -> s.loadingScore
                                            loadingSeconds < 20 -> s.loadingSlow
                                            loadingSeconds < 30 -> s.loadingSlower
                                            else -> s.loadingFailed
                                        }
                                        Text(
                                            text = loadingText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = windScoreColors().ink,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = WindScoreSpacing.lg)
                                        )
                                    }
                                }
                            } else {
                                NativeSheetView(
                                    snapshot = sheetSnapshot!!,
                                    currentTickProvider = { tick },
                                    onSeek = onSeek,
                                    onStepForward = onStepForward,
                                    onStepBackward = onStepBackward,
                                    pendingLoopStart = pendingLoopStart,
                                    loopRange = activeLoopRange,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(sheetFraction)
                                        .clip(RoundedCornerShape(WindScoreRounded.lg))
                                        .background(Color.White),
                                )
                            }
                        }
                    }
                }
            }

            // 静态键盘（通过 alpha 动画过渡）
            val keyboardAlpha by animateFloatAsState(
                targetValue = if (showSheetKb) 1f else 0f,
                label = "keyboardAlpha"
            )
            if (keyboardAlpha > 0.01f) {
                PracticeKeyboard(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.28f)
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(WindScoreRounded.lg))
                        .alpha(keyboardAlpha),
                    pitchLow = session.score.notes.minOf { it.pitch }.coerceIn(21, 108),
                    pitchHigh = session.score.notes.maxOf { it.pitch }.coerceIn(21, 108),
                    targetPitches = targetPitches,
                    hostedPitches = hostedTargetPitches,
                    heldPitches = activePitches,
                )
            }

            // 评分结算覆盖层：覆盖主舞台，显示最终分数 + 回到练习
            AnimatedVisibility(
                visible = scoringPhase == ScoringPhase.RESULT,
                enter = fadeIn(animationSpec = tween(durationMillis = 600)),
                exit = fadeOut(animationSpec = tween(durationMillis = 400))
            ) {
                ScoringResultOverlay(
                    session = session,
                    onReturnToPractice = onReturnToPractice,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun StatusCard(session: ScoreSession, modifier: Modifier = Modifier) {
    val s = strings()
    val ss = session.state.collectAsState().value
    val pending = ss.pendingPitches.joinToString { pitchName(it) }.ifEmpty { s.none }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WindScoreRounded.lg))
            .background(windScoreColors().canvas)
            .border(1.dp, windScoreColors().hairline, RoundedCornerShape(WindScoreRounded.lg))
            .padding(WindScoreSpacing.md),
        verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)
    ) {
        Text(
            text = s.statusLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W600, fontSize = 10.sp),
            color = windScoreColors().ink.copy(alpha = 0.5f)
        )

        Spacer(Modifier.height(2.dp))

        // 该弹
        Column {
            Text(
                text = s.toPlay,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = windScoreColors().ink.copy(alpha = 0.5f)
            )
            Text(
                text = if (ss.finished) s.performanceComplete else pending,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W500),
                color = if (ss.finished) windScoreColors().semanticSuccess else windScoreColors().ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(2.dp))

        // 进度 & TICK
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = s.progress,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = windScoreColors().ink.copy(alpha = 0.5f)
                )
                Text(
                    text = "${ss.progress}/${ss.groupCount}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W500),
                    color = windScoreColors().ink
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TICK",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = windScoreColors().ink.copy(alpha = 0.5f)
                )
                Text(
                    text = "${ss.currentTick}/${session.score.totalTicks}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W500),
                    color = windScoreColors().ink
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Linear Progress Bar (长条进度条)
        val progressRatio = (ss.currentTick.toFloat() / session.score.totalTicks.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
        androidx.compose.material3.LinearProgressIndicator(
            progress = { progressRatio },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = windScoreColors().accentMagenta,
            trackColor = windScoreColors().hairlineSoft
        )
    }
}
