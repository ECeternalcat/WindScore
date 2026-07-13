package com.windscore.ui

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
) {
    var toastMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2000)
            toastMessage = null
        }
    }

    val scope = rememberCoroutineScope()
    var countdownEnabled by remember { mutableStateOf(false) }
    var countdownSeconds by remember { mutableStateOf(0) }
    var countdownJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var metronomeVol by remember { mutableStateOf(50) }
    var metronomePrevVol by remember { mutableStateOf(50) } // 记住静音前音量

    LaunchedEffect(player, metronomeVol) {
        player?.metronomeVolume = metronomeVol
    }

    fun toggleMute() {
        if (metronomeVol > 0) {
            metronomePrevVol = metronomeVol
            metronomeVol = 0
        } else {
            metronomeVol = if (metronomePrevVol > 0) metronomePrevVol else 50
        }
    }

    val sheetReady = viewMode == ViewMode.ROLL || renderMs != null

    LaunchedEffect(spacePressed) {
        if (spacePressed > 0 && sheetReady) {
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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PlayerToolbar(
                title = score.title,
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
                )
                MainStage(
                    session = session,
                    viewMode = viewMode,
                    playTick = playTick,
                    isPlaying = isPlaying,
                    playingPitches = playingPitches,
                    onSeek = onSeek,
                    onStepForward = onStepForward,
                    onStepBackward = onStepBackward,
                    renderMs = renderMs,
                    isCompact = isCompact,
                    onRenderTime = onRenderTime,
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
                        session = session,
                        viewMode = viewMode,
                        playTick = playTick,
                        isPlaying = isPlaying,
                        playingPitches = playingPitches,
                        onSeek = onSeek,
                        onStepForward = onStepForward,
                        onStepBackward = onStepBackward,
                        renderMs = renderMs,
                        isCompact = isCompact,
                        onRenderTime = onRenderTime,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    Column(
                        modifier = Modifier
                            .width(300.dp)
                            .fillMaxHeight(),
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
                            onToggleMidi = onToggleMidi,
                            onReset = onReset,
                            onShowToast = { toastMessage = it },
                            countdownEnabled = countdownEnabled,
                            onCountdownEnabledChange = { countdownEnabled = it },
                            metronomeVol = metronomeVol,
                            onMetronomeVolChange = { metronomeVol = it },
                            onToggleMute = ::toggleMute,
                            onModeChange = onModeChange,
                            onViewChange = onViewChange,
                            onLoopStart = onLoopStart,
                            onLoopEnd = onLoopEnd,
                            onClearLoop = onClearLoop,
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
            }
        }

        toastMessage?.let { msg ->
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
                    contentDescription = "返回",
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
                        contentDescription = "重置",
                        modifier = Modifier.size(20.dp),
                    )
                }
                PillButton(style = if (running) PillButtonStyle.SECONDARY else PillButtonStyle.PRIMARY, onClick = onToggleMidi) {
                    Text(if (running) "停止 MIDI" else "开始 MIDI")
                }
            }
            CircularIconButton(onClick = onShowSettings) {
                Icon(
                    painter = painterResource(Res.drawable.gear_six_thin),
                    contentDescription = "设置",
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
) {
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
                    label = "双手",
                )
                PillToggle(
                    selected = practiceMode == PracticeMode.RIGHT_ONLY,
                    onClick = { onModeChange(PracticeMode.RIGHT_ONLY) },
                    label = "右手",
                )
                PillToggle(
                    selected = practiceMode == PracticeMode.LEFT_ONLY,
                    onClick = { onModeChange(PracticeMode.LEFT_ONLY) },
                    label = "左手",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)) {
                CircularIconButton(
                    onClick = { onViewChange(ViewMode.ROLL) },
                    selected = viewMode == ViewMode.ROLL,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.piano_keys_thin),
                        contentDescription = "卷帘",
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
                            contentDescription = "识谱",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    CircularIconButton(
                        onClick = { onViewChange(ViewMode.SHEET_FULL) },
                        selected = viewMode == ViewMode.SHEET_FULL,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.music_notes_thin),
                            contentDescription = "看谱",
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
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(20.dp),
                    )
                }
                CircularIconButton(onClick = onStopPlay, enabled = sheetReady) {
                    Icon(
                        painter = painterResource(Res.drawable.stop_thin),
                        contentDescription = "停止",
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
                CircularIconButton(onClick = onLoopStart, enabled = !session.isLoopActive) {
                    Icon(
                        painter = painterResource(Res.drawable.repeat_thin),
                        contentDescription = "设起点",
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (pendingLoopStart != null && !session.isLoopActive) {
                    Text(
                        "起点 $pendingLoopStart",
                        style = MaterialTheme.typography.bodySmall,
                        color = WindScoreColors.ink,
                    )
                }
                CircularIconButton(onClick = onLoopEnd, enabled = !session.isLoopActive && pendingLoopStart != null) {
                    Icon(
                        painter = painterResource(Res.drawable.repeat_thin),
                        contentDescription = "设终点",
                        modifier = Modifier.size(20.dp),
                    )
                }
                CircularIconButton(onClick = onClearLoop, enabled = session.isLoopActive) {
                    Icon(
                        painter = painterResource(Res.drawable.x_thin),
                        contentDescription = "清除循环",
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (session.isLoopActive) {
                    Text(
                        "循环 ${session.loopRange.first} ~ ${session.loopRange.last}",
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val pending = session.pendingPitches.joinToString { pitchName(it) }
        Text(
            if (session.finished) "演奏完成 · 准度 ${session.scorePercent()}%" else "该弹: $pending",
            style = MaterialTheme.typography.bodySmall,
            color = if (session.finished) WindScoreColors.semanticSuccess else WindScoreColors.ink,
        )
        Text(
            "进度 ${session.progress}/${session.groupCount} · tick ${session.currentTick}/${session.score.totalTicks}",
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
    onSeek: (Long) -> Unit,
    onStepForward: () -> Unit,
    onStepBackward: () -> Unit,
    renderMs: Double?,
    isCompact: Boolean,
    onRenderTime: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
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

            // 卷帘 — 始终挂载
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
                                loadingSeconds < 10 -> "正在加载乐谱…"
                                loadingSeconds < 20 -> "加载时间比平时更久，请稍后…"
                                loadingSeconds < 30 -> "看来还需要一会儿才可以…"
                                else -> "抱歉，加载可能失败了，建议您附带乐谱文件进行反馈…"
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
            // 结算页
            if (session.finished) {
                ResultScreen(
                    session = session,
                    modifier = Modifier.fillMaxSize(),
                    onReset = { session.reset() },
                )
            }
        }
    }
}

@Composable
private fun ControlSidebar(
    session: ScoreSession,
    practiceMode: PracticeMode,
    viewMode: ViewMode,
    pendingLoopStart: Long?,
    scoreHasSheet: Boolean,
    isPlaying: Boolean,
    sheetReady: Boolean,
    running: Boolean,
    player: ScorePlayer?,
    onToggleMidi: () -> Unit,
    onReset: () -> Unit,
    onShowToast: (String) -> Unit,
    countdownEnabled: Boolean,
    onCountdownEnabledChange: (Boolean) -> Unit,
    metronomeVol: Int,
    onMetronomeVolChange: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onModeChange: (PracticeMode) -> Unit,
    onViewChange: (ViewMode) -> Unit,
    onLoopStart: () -> Unit,
    onLoopEnd: () -> Unit,
    onClearLoop: () -> Unit,
    onTogglePlay: () -> Unit,
    onStopPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WindScoreRounded.lg))
            .background(WindScoreColors.canvas)
            .border(1.dp, WindScoreColors.hairline, RoundedCornerShape(WindScoreRounded.lg))
            .padding(WindScoreSpacing.md),
        verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.md),
    ) {
        // Top: 启动midi接入 (Start MIDI connection)
        PillButton(
            style = if (running) PillButtonStyle.SECONDARY else PillButtonStyle.PRIMARY,
            onClick = onToggleMidi,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (running) "断开 MIDI 接入" else "启动 MIDI 接入")
        }

        // Group 1: 模式 (Mode)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, WindScoreColors.hairlineSoft, RoundedCornerShape(WindScoreRounded.md))
                .padding(WindScoreSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)
        ) {
            Text("模式", style = MaterialTheme.typography.labelSmall, color = WindScoreColors.ink.copy(alpha = 0.5f))
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)
            ) {
                PillToggle(practiceMode == PracticeMode.BOTH, { onModeChange(PracticeMode.BOTH) }, "双手", modifier = Modifier.weight(1f))
                PillToggle(practiceMode == PracticeMode.RIGHT_ONLY, { onModeChange(PracticeMode.RIGHT_ONLY) }, "右手", modifier = Modifier.weight(1f))
                PillToggle(practiceMode == PracticeMode.LEFT_ONLY, { onModeChange(PracticeMode.LEFT_ONLY) }, "左手", modifier = Modifier.weight(1f))
            }
        }

        // Group 2: 视图与播放 (View & Play)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, WindScoreColors.hairlineSoft, RoundedCornerShape(WindScoreRounded.md))
                .padding(WindScoreSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.md)
            ) {
                // Left: View Mode Group
                Column(modifier = Modifier.weight(3f)) {
                    Text("视图", style = MaterialTheme.typography.labelSmall, color = WindScoreColors.ink.copy(alpha = 0.5f))
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)) {
                        CircularIconButton(onClick = { onViewChange(ViewMode.ROLL) }, selected = viewMode == ViewMode.ROLL) {
                            Icon(painterResource(Res.drawable.piano_keys_thin), "卷帘", Modifier.size(20.dp))
                        }
                        if (scoreHasSheet) {
                            CircularIconButton(onClick = { onViewChange(ViewMode.SHEET_KEYBOARD) }, selected = viewMode == ViewMode.SHEET_KEYBOARD) {
                                Icon(painterResource(Res.drawable.book_open_text_thin), "识谱", Modifier.size(20.dp))
                            }
                            CircularIconButton(onClick = { onViewChange(ViewMode.SHEET_FULL) }, selected = viewMode == ViewMode.SHEET_FULL) {
                                Icon(painterResource(Res.drawable.music_notes_thin), "看谱", Modifier.size(20.dp))
                            }
                        }
                    }
                }

                // Right: Playback Control Group
                Column(modifier = Modifier.weight(2f)) {
                    Text("播放", style = MaterialTheme.typography.labelSmall, color = WindScoreColors.ink.copy(alpha = 0.5f))
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)) {
                        CircularIconButton(onClick = onTogglePlay, enabled = sheetReady) {
                            Icon(
                                painterResource(if (isPlaying) Res.drawable.pause_thin else Res.drawable.play_thin),
                                if (isPlaying) "暂停" else "播放", Modifier.size(20.dp),
                            )
                        }
                        CircularIconButton(onClick = onStopPlay, enabled = sheetReady) {
                            Icon(
                                painterResource(Res.drawable.stop_thin), "停止", Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }

        // Speed group (播放速度)
        var speed by remember(player) { mutableStateOf((player?.speedMultiplier ?: 1.0).toFloat()) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, WindScoreColors.hairlineSoft, RoundedCornerShape(WindScoreRounded.md))
                .padding(WindScoreSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("速度", style = MaterialTheme.typography.labelSmall, color = WindScoreColors.ink.copy(alpha = 0.5f))
                Text(
                    text = "${(speed * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
                    color = WindScoreColors.ink
                )
            }

            // Slider (拖动条)
            androidx.compose.material3.Slider(
                value = speed,
                onValueChange = { newValue ->
                    if (isPlaying) {
                        onShowToast("请暂停后调整速度")
                    } else {
                        speed = newValue.coerceIn(0.0f, 2.0f)
                        player?.speedMultiplier = speed.toDouble()
                    }
                },
                valueRange = 0.0f..2.0f,
                modifier = Modifier.fillMaxWidth().height(24.dp),
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = WindScoreColors.primary,
                    activeTrackColor = WindScoreColors.primary,
                    inactiveTrackColor = WindScoreColors.hairlineSoft
                )
            )

            // Speed Presets Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)
            ) {
                listOf(0.5f to "x0.5", 1.0f to "原速", 1.5f to "x1.5").forEach { (value, label) ->
                    val isSelected = speed == value
                    PillToggle(
                        selected = isSelected,
                        onClick = {
                            if (isPlaying) {
                                onShowToast("请暂停后调整速度")
                            } else {
                                speed = value
                                player?.speedMultiplier = speed.toDouble()
                            }
                        },
                        label = label,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Group 3: 循环 (Loop)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, WindScoreColors.hairlineSoft, RoundedCornerShape(WindScoreRounded.md))
                .padding(WindScoreSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)
        ) {
            Text("循环", style = MaterialTheme.typography.labelSmall, color = WindScoreColors.ink.copy(alpha = 0.5f))
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Loop Start
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xxs)) {
                    CircularIconButton(onClick = onLoopStart, enabled = !session.isLoopActive) {
                        Icon(painterResource(Res.drawable.repeat_thin), "起点", Modifier.size(20.dp))
                    }
                    Text("起点", style = MaterialTheme.typography.bodySmall, color = WindScoreColors.ink)
                }

                // Loop End
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xxs)) {
                    CircularIconButton(onClick = onLoopEnd, enabled = !session.isLoopActive && pendingLoopStart != null) {
                        Icon(painterResource(Res.drawable.repeat_thin), "终点", Modifier.size(20.dp))
                    }
                    Text("终点", style = MaterialTheme.typography.bodySmall, color = WindScoreColors.ink)
                }

                // Clear
                CircularIconButton(onClick = onClearLoop, enabled = session.isLoopActive) {
                    Icon(painterResource(Res.drawable.x_thin), "清除", Modifier.size(20.dp))
                }
            }
            if (session.isLoopActive) {
                Text("循环 ${session.loopRange.first}~${session.loopRange.last}",
                    style = MaterialTheme.typography.bodySmall, color = WindScoreColors.accentMagenta)
            } else if (pendingLoopStart != null) {
                Text("起点 $pendingLoopStart", style = MaterialTheme.typography.bodySmall, color = WindScoreColors.ink)
            }
        }

        // Group 4: 节拍器 (Metronome)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, WindScoreColors.hairlineSoft, RoundedCornerShape(WindScoreRounded.md))
                .padding(WindScoreSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("节拍器", style = MaterialTheme.typography.labelSmall, color = WindScoreColors.ink.copy(alpha = 0.5f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)
                ) {
                    Text("预备拍", style = MaterialTheme.typography.bodySmall, color = WindScoreColors.ink.copy(alpha = 0.5f))
                    CustomSwitch(
                        checked = countdownEnabled,
                        onCheckedChange = onCountdownEnabledChange
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)
            ) {
                Icon(
                    painter = painterResource(
                        if (metronomeVol > 0) Res.drawable.speaker_high_thin else Res.drawable.speaker_slash_thin
                    ),
                    contentDescription = "节拍器音量",
                    modifier = Modifier.size(20.dp).clip(RoundedCornerShape(WindScoreRounded.full))
                        .clickable(onClick = onToggleMute),
                    tint = WindScoreColors.ink.copy(alpha = 0.5f)
                )
                androidx.compose.material3.Slider(
                    value = metronomeVol.toFloat(),
                    onValueChange = { newValue ->
                        onMetronomeVolChange(newValue.toInt())
                    },
                    valueRange = 0.0f..100.0f,
                    modifier = Modifier.weight(1f).height(24.dp),
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = WindScoreColors.primary,
                        activeTrackColor = WindScoreColors.primary,
                        inactiveTrackColor = WindScoreColors.hairlineSoft
                    )
                )
                Text(
                    text = "${metronomeVol}%",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
                    color = WindScoreColors.ink,
                    modifier = Modifier.width(44.dp),
                    textAlign = TextAlign.End
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Bottom: 重新加载 (Reload)
        PillButton(
            style = PillButtonStyle.SECONDARY,
            onClick = onReset,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.arrows_counter_clockwise_thin),
                    contentDescription = "重新加载",
                    modifier = Modifier.size(16.dp)
                )
                Text("重新加载")
            }
        }
    }
}

@Composable
private fun CustomSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val background = if (checked) WindScoreColors.primary else WindScoreColors.hairlineSoft
    val alignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    Box(
        modifier = modifier
            .width(40.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(background)
            .clickable { onCheckedChange(!checked) }
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White)
                .align(alignment)
        )
    }
}

@Composable
private fun PillToggle(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    PillButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        style = if (selected) PillButtonStyle.PRIMARY else PillButtonStyle.SECONDARY,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600, fontSize = 12.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CircularIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    val background = when {
        selected -> WindScoreColors.primary
        else -> WindScoreColors.surfaceSoft
    }
    val contentColor = when {
        selected -> WindScoreColors.onPrimary
        else -> WindScoreColors.ink
    }
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(WindScoreRounded.full))
            .background(if (enabled) background else WindScoreColors.hairlineSoft)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (enabled) contentColor else contentColor.copy(alpha = 0.4f),
        ) {
            content()
        }
    }
}

private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

fun pitchName(pitch: Int): String {
    val name = NOTE_NAMES[pitch.mod(12)]
    val octave = pitch / 12 - 1
    return "$name$octave"
}

@Composable
private fun StatusCard(session: ScoreSession, modifier: Modifier = Modifier) {
    val pending = session.pendingPitches.joinToString { pitchName(it) }.ifEmpty { "无" }
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
            text = "STATUS 实时状态",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W600, fontSize = 10.sp),
            color = WindScoreColors.ink.copy(alpha = 0.5f)
        )

        Spacer(Modifier.height(2.dp))

        // 该弹
        Column {
            Text(
                text = "该弹",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = WindScoreColors.ink.copy(alpha = 0.5f)
            )
            Text(
                text = if (session.finished) "演奏完成 · 准度 ${session.scorePercent()}%" else pending,
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
                    text = "进度",
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
