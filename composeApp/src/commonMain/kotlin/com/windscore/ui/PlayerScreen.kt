package com.windscore.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
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
import androidx.compose.runtime.rememberCoroutineScope
import windscore.composeapp.generated.resources.clock_thin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.windscore.core.engine.ScorePlayer
import com.windscore.core.engine.ScoreSession
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
import com.windscore.ui.theme.WindScoreColors
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
enum class SidebarTab { PRACTICE, SCORING }

/** 评分流程的三个子阶段（仅在评分 Tab 内推进）。 */
enum class ScoringPhase { SETUP, ACTIVE, RESULT }

@Composable
fun PlayerScreen(
    score: Score,
    session: ScoreSession,
    isCompact: Boolean,
    running: Boolean,
    practiceMode: PracticeMode,
    viewMode: ViewMode,
    pendingLoopStart: Long?,
    player: ScorePlayer?,
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
                            countdownSeconds = 3
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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
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
            if (isCompact) {
                PlayerControlBar(
                    session = session,
                    practiceMode = practiceMode,
                    viewMode = viewMode,
                    pendingLoopStart = pendingLoopStart,
                    scoreHasSheet = score.rawXml != null,
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
                                                scoreHasSheet = score.rawXml != null,
                                                isPlaying = isPlaying,
                                                sheetReady = sheetReady,
                                                running = running,
                                                player = player,
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
                                                                countdownSeconds = 3
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
                                                    scoreHasSheet = score.rawXml != null,
                                                    viewMode = viewMode,
                                                    onViewChange = onViewChange,
                                                    onStartScoring = onStartScoring,
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
                                modifier = Modifier.weight(1f)
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
                                modifier = Modifier.weight(1f)
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

        (toastMessage ?: localToastMessage)?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(WindScoreRounded.md),
                    color = WindScoreColors.ink,
                    contentColor = Color.White,
                    shadowElevation = 4.dp,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = msg,
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
                color = WindScoreColors.ink,
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
    val barVPad = if (isCompact) WindScoreSpacing.sm else WindScoreSpacing.xs
    val barGap = if (isCompact) WindScoreSpacing.sm else WindScoreSpacing.xs
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = WindScoreSpacing.lg, vertical = barVPad),
        verticalArrangement = Arrangement.spacedBy(barGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)) {
                PillToggle(
                    selected = practiceMode == PracticeMode.BOTH,
                    onClick = { onModeChange(PracticeMode.BOTH) },
                    label = s.bothHands,
                )
                PillToggle(
                    selected = practiceMode == PracticeMode.RIGHT_ONLY,
                    onClick = { onModeChange(PracticeMode.RIGHT_ONLY) },
                    label = s.rightHand,
                )
                PillToggle(
                    selected = practiceMode == PracticeMode.LEFT_ONLY,
                    onClick = { onModeChange(PracticeMode.LEFT_ONLY) },
                    label = s.leftHand,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)) {
                CircularIconButton(
                    onClick = { onViewChange(ViewMode.ROLL) },
                    selected = viewMode == ViewMode.ROLL,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.piano_keys_thin),
                        contentDescription = s.pianoRoll,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (scoreHasSheet) {
                    CircularIconButton(
                        onClick = { onViewChange(ViewMode.SHEET_KEYBOARD) },
                        selected = viewMode == ViewMode.SHEET_KEYBOARD,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.book_open_text_thin),
                            contentDescription = s.readSheet,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    CircularIconButton(
                        onClick = { onViewChange(ViewMode.SHEET_FULL) },
                        selected = viewMode == ViewMode.SHEET_FULL,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.music_notes_thin),
                            contentDescription = s.viewSheet,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs),
            ) {
                CircularIconButton(onClick = onTogglePlay, enabled = sheetReady) {
                    Icon(
                        painter = painterResource(if (isPlaying) Res.drawable.pause_thin else Res.drawable.play_thin),
                        contentDescription = if (isPlaying) s.pause else s.play,
                        modifier = Modifier.size(20.dp),
                    )
                }
                CircularIconButton(onClick = onStopPlay, enabled = sheetReady) {
                    Icon(
                        painter = painterResource(Res.drawable.stop_thin),
                        contentDescription = s.stop,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    "${(session.currentTick * 100 / (session.score.totalTicks.coerceAtLeast(1))).coerceIn(0, 100)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = WindScoreColors.ink,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs),
            ) {
                CircularIconButton(onClick = onLoopStart, enabled = activeLoopRange == null) {
                    Icon(
                        painter = painterResource(Res.drawable.repeat_thin),
                        contentDescription = s.setLoopStart,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (pendingLoopStart != null && activeLoopRange == null) {
                    Text(
                        s.loopStartLabel(pendingLoopStart),
                        style = MaterialTheme.typography.bodySmall,
                        color = WindScoreColors.ink,
                    )
                }
                CircularIconButton(onClick = onLoopEnd, enabled = activeLoopRange == null && pendingLoopStart != null) {
                    Icon(
                        painter = painterResource(Res.drawable.repeat_thin),
                        contentDescription = s.setLoopEnd,
                        modifier = Modifier.size(20.dp),
                    )
                }
                CircularIconButton(onClick = onClearLoop, enabled = activeLoopRange != null) {
                    Icon(
                        painter = painterResource(Res.drawable.x_thin),
                        contentDescription = s.clearLoop,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (activeLoopRange != null) {
                    Text(
                        s.loopRangeLabel(activeLoopRange.first, activeLoopRange.last),
                        style = MaterialTheme.typography.bodySmall,
                        color = WindScoreColors.accentMagenta,
                    )
                }
            }
        }

        StatusLine(session, viewMode, sheetReady)
    }
}

@Composable
private fun StatusLine(session: ScoreSession, viewMode: ViewMode = ViewMode.ROLL, sheetReady: Boolean = true) {
    val s = strings()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val pending = session.pendingPitches.joinToString { pitchName(it) }
        Text(
            if (session.finished) s.finishedAccuracy(session.scorePercent()) else s.toPlayPending(pending),
            style = MaterialTheme.typography.bodySmall,
            color = if (session.finished) WindScoreColors.semanticSuccess else WindScoreColors.ink,
        )
        Text(
            s.progressTick(session.progress, session.groupCount, session.currentTick, session.score.totalTicks),
            style = MaterialTheme.typography.bodySmall,
            color = WindScoreColors.ink,
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
    val frameH = if (isCompact) WindScoreSpacing.sm else 0.dp
    val frameV = if (isCompact) WindScoreSpacing.sm else 0.dp
    val innerPadding = if (isCompact) WindScoreSpacing.lg else WindScoreSpacing.sm
    val tick = if (isPlaying || playTick > 0) playTick else session.currentTick
    val showRoll = viewMode == ViewMode.ROLL
    val showSheetKb = viewMode == ViewMode.SHEET_KEYBOARD
    val showSheetFull = viewMode == ViewMode.SHEET_FULL
    val sheetVisible = showSheetKb || showSheetFull

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = frameH, vertical = frameV)
            .clip(RoundedCornerShape(WindScoreRounded.lg))
            .background(WindScoreColors.blockLime),
    ) {
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            val rawXml = session.score.rawXml
            val activePitches = if (isPlaying) playingPitches else session.heldPitches
            val targetPitches = if (isPlaying) playingPitches else session.pendingPitches

            PianoRoll(
                score = session.score,
                currentTickProvider = { tick },
                modifier = Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(WindScoreRounded.lg))
                    .alpha(if (showRoll) 1f else 0f),
                hostedTrack = session.hostedTrack,
                heldPitchesProvider = { activePitches },
                pulseIntensityProvider = { session.pulseIntensity },
                bpmProvider = { session.score.tempo.takeIf { it > 0 } ?: 120 },
                pendingLoopStart = pendingLoopStart,
                loopRange = activeLoopRange,
            )
            // 五线谱 — 预渲染快照模式
            if (rawXml != null) {
                val sheetFraction = if (showSheetKb) 0.72f else 1f
                var sheetSnapshot by remember(rawXml) { mutableStateOf<SheetSnapshot?>(null) }
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

                // WebView 渲染器：始终在屏幕外后台渲染，用户永不可见
                ScoreSheetView(
                    rawXml = rawXml,
                    currentTickProvider = { tick },
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(sheetFraction)
                        .offset(x = 99999.dp),
                    divisions = session.score.divisions,
                    onRenderTime = onRenderTime,
                    onSnapshot = { snap -> sheetSnapshot = snap },
                )

                // 快照就绪前：显示加载指示器（带圆盘进度动画和微调文本）
                if (sheetSnapshot == null && sheetVisible) {
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
                                color = WindScoreColors.accentMagenta,
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
                                color = WindScoreColors.ink,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = WindScoreSpacing.lg)
                            )
                        }
                    }
                }

                // 快照就绪后：显示 NativeSheetView（纯 Compose Canvas，带圆角和白底隔离）
                if (sheetSnapshot != null && sheetVisible) {
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
            // 静态键盘 — 模式二下半部分
            StaticKeyboard(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.28f)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(WindScoreRounded.lg))
                    .alpha(if (showSheetKb) 1f else 0f),
                pitchLow = session.score.notes.minOf { it.pitch }.coerceIn(21, 108),
                pitchHigh = session.score.notes.maxOf { it.pitch }.coerceIn(21, 108),
                pendingPitchesProvider = { targetPitches },
                heldPitchesProvider = { activePitches },
            )
            // 练习结算页（评分结算由下方 overlay 单独处理，避免重复）
            if (session.finished && scoringPhase != ScoringPhase.RESULT) {
                ResultScreen(
                    session = session,
                    modifier = Modifier.fillMaxSize(),
                    onReset = { session.reset() },
                )
            }
            // 评分结算覆盖层：覆盖主舞台，显示最终分数 + 回到练习
            if (scoringPhase == ScoringPhase.RESULT) {
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
    val pending = session.pendingPitches.joinToString { pitchName(it) }.ifEmpty { s.none }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WindScoreRounded.lg))
            .background(WindScoreColors.canvas)
            .border(1.dp, WindScoreColors.hairline, RoundedCornerShape(WindScoreRounded.lg))
            .padding(WindScoreSpacing.md),
        verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)
    ) {
        Text(
            text = s.statusLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W600, fontSize = 10.sp),
            color = WindScoreColors.ink.copy(alpha = 0.5f)
        )

        Spacer(Modifier.height(2.dp))

        // 该弹
        Column {
            Text(
                text = s.toPlay,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = WindScoreColors.ink.copy(alpha = 0.5f)
            )
            Text(
                text = if (session.finished) s.finishedAccuracy(session.scorePercent()) else pending,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W500),
                color = if (session.finished) WindScoreColors.semanticSuccess else WindScoreColors.ink,
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
                    color = WindScoreColors.ink.copy(alpha = 0.5f)
                )
                Text(
                    text = "${session.progress}/${session.groupCount}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W500),
                    color = WindScoreColors.ink
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TICK",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = WindScoreColors.ink.copy(alpha = 0.5f)
                )
                Text(
                    text = "${session.currentTick}/${session.score.totalTicks}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W500),
                    color = WindScoreColors.ink
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Linear Progress Bar (长条进度条)
        val progressRatio = (session.currentTick.toFloat() / session.score.totalTicks.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
        androidx.compose.material3.LinearProgressIndicator(
            progress = progressRatio,
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = WindScoreColors.accentMagenta,
            trackColor = WindScoreColors.hairlineSoft
        )
    }
}
