package com.windscore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.windscore.audio.createAudioSynth
import com.windscore.core.engine.ScorePlayer
import com.windscore.core.engine.ScoreSession
import com.windscore.core.loadScore
import com.windscore.core.model.PracticeMode
import com.windscore.core.model.Score
import com.windscore.midi.MidiEvent
import com.windscore.midi.MidiManager
import com.windscore.ui.PianoRoll
import com.windscore.ui.ResultScreen
import com.windscore.ui.ScoreFilePickerButton
import com.windscore.ui.ScoreSheetView
import com.windscore.ui.StaticKeyboard
import com.windscore.ui.components.PillButton
import com.windscore.ui.components.PillButtonStyle
import com.windscore.ui.theme.WindScoreColors
import com.windscore.ui.theme.WindScoreRounded
import com.windscore.ui.theme.WindScoreSpacing
import com.windscore.ui.theme.WindScoreTheme
import windscore.composeapp.generated.resources.Res
import windscore.composeapp.generated.resources.arrow_left_thin
import windscore.composeapp.generated.resources.arrows_counter_clockwise_thin
import windscore.composeapp.generated.resources.book_open_text_thin
import windscore.composeapp.generated.resources.clock_thin
import windscore.composeapp.generated.resources.music_notes_thin
import windscore.composeapp.generated.resources.pause_thin
import windscore.composeapp.generated.resources.piano_keys_thin
import windscore.composeapp.generated.resources.play_thin
import windscore.composeapp.generated.resources.plus_thin
import windscore.composeapp.generated.resources.repeat_thin
import windscore.composeapp.generated.resources.stop_thin
import windscore.composeapp.generated.resources.x_thin
import org.jetbrains.compose.resources.painterResource

enum class ViewMode { ROLL, SHEET_KEYBOARD, SHEET_FULL }

@Composable
fun App(
    midiManager: MidiManager,
    isCompact: Boolean = false,
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

    LaunchedEffect(running) {
        if (running) {
            synth.start()
            midiManager.start()
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
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            midiManager.stop()
            synth.stop()
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
        session = null
        currentScore = null
        player?.stop()
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
                    onBack = { closeScore() },
                    onToggleMidi = {
                        running = !running
                        if (!running) {
                            midiManager.stop()
                            synth.stop()
                        }
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
                    onTogglePlay = { player?.toggle() },
                    onStopPlay = { player?.stop() },
                    onRenderTime = { renderMs = it },
                )
            }
        }
    }
}

@Composable
private fun WelcomeScreen(
    isCompact: Boolean,
    recentScores: List<Score>,
    onLoadDefault: () -> Unit,
    onScoreClick: (Score) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        WelcomeTopBar()
        if (isCompact) {
            ScoreListBody(
                recentScores = recentScores,
                onLoadDefault = onLoadDefault,
                onScoreClick = onScoreClick,
                modifier = Modifier.weight(1f),
            )
        } else {
            DesktopWelcomeBody(
                recentScores = recentScores,
                onLoadDefault = onLoadDefault,
                onPickFile = onScoreClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WelcomeTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = WindScoreSpacing.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "WindScore",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W700),
            )
            Spacer(Modifier.width(WindScoreSpacing.sm))
            Text(
                "钢琴伴学",
                style = MaterialTheme.typography.bodySmall,
                color = WindScoreColors.ink,
            )
        }
    }
}

@Composable
private fun DesktopWelcomeBody(
    recentScores: List<Score>,
    onLoadDefault: () -> Unit,
    onPickFile: (Score) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(WindScoreSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.lg),
    ) {
        // 左侧：添加乐谱大卡片
        AddScoreCard(
            onLoadDefault = onLoadDefault,
            onScoreClick = onPickFile,
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight(),
        )
        // 右侧：最近乐谱
            RecentScoresPanel(
                scores = recentScores,
                onScoreClick = onPickFile,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
    }
}

@Composable
private fun AddScoreCard(
    onLoadDefault: () -> Unit,
    onScoreClick: (Score) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(WindScoreRounded.lg))
            .clickable { onLoadDefault() },
        color = WindScoreColors.blockLime,
        shape = RoundedCornerShape(WindScoreRounded.lg),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(WindScoreRounded.lg))
                    .background(WindScoreColors.canvas),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.plus_thin),
                    contentDescription = "添加乐谱",
                    modifier = Modifier.size(48.dp),
                    tint = WindScoreColors.ink,
                )
            }
            Spacer(Modifier.height(WindScoreSpacing.lg))
            Text(
                "添加乐谱",
                style = MaterialTheme.typography.headlineLarge,
                color = WindScoreColors.ink,
            )
            Spacer(Modifier.height(WindScoreSpacing.sm))
            Text(
                "点击卡片加载示例，或点击下方按钮导入 MusicXML",
                style = MaterialTheme.typography.bodyMedium,
                color = WindScoreColors.ink,
            )
            Spacer(Modifier.height(WindScoreSpacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.sm)) {
            ScoreFilePickerButton(onPicked = onScoreClick, style = PillButtonStyle.SECONDARY)
            }
        }
    }
}

@Composable
private fun RecentScoresPanel(
    scores: List<Score>,
    onScoreClick: (Score) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(WindScoreRounded.lg)),
        color = WindScoreColors.surfaceSoft,
        shape = RoundedCornerShape(WindScoreRounded.lg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindScoreSpacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(Res.drawable.clock_thin),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = WindScoreColors.ink,
                )
                Spacer(Modifier.width(WindScoreSpacing.xs))
                Text(
                    "最近打开的乐谱",
                    style = MaterialTheme.typography.titleMedium,
                    color = WindScoreColors.ink,
                )
            }
            Spacer(Modifier.height(WindScoreSpacing.md))
            if (scores.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "暂无最近记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WindScoreColors.ink,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.sm)) {
                    items(scores, key = { it.title + it.totalTicks }) { score ->
                        ScoreListItem(
                            score = score,
                            onClick = { onScoreClick(score) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreListBody(
    recentScores: List<Score>,
    onLoadDefault: () -> Unit,
    onScoreClick: (Score) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(WindScoreSpacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.sm),
        ) {
            PillButton(style = PillButtonStyle.PRIMARY, onClick = onLoadDefault) {
                Icon(
                    painter = painterResource(Res.drawable.plus_thin),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(WindScoreSpacing.xxs))
                Text("示例乐谱")
            }
            ScoreFilePickerButton(onPicked = onScoreClick, style = PillButtonStyle.SECONDARY)
        }
        Spacer(Modifier.height(WindScoreSpacing.lg))
        Text(
            "乐谱库",
            style = MaterialTheme.typography.headlineLarge,
            color = WindScoreColors.ink,
        )
        Spacer(Modifier.height(WindScoreSpacing.sm))
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(WindScoreRounded.lg)),
            color = WindScoreColors.surfaceSoft,
            shape = RoundedCornerShape(WindScoreRounded.lg),
        ) {
            if (recentScores.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "点击上方按钮加载第一首乐谱",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WindScoreColors.ink,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(WindScoreSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.sm),
                ) {
                    items(recentScores, key = { it.title + it.totalTicks }) { score ->
                    ScoreListItem(
                        score = score,
                        onClick = { onScoreClick(score) },
                    )
                }
                }
            }
        }
    }
}

@Composable
private fun ScoreListItem(
    score: Score,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WindScoreRounded.md))
            .clickable(onClick = onClick),
        color = WindScoreColors.canvas,
        shape = RoundedCornerShape(WindScoreRounded.md),
    ) {
        Row(
            modifier = Modifier.padding(WindScoreSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(WindScoreRounded.md))
                    .background(WindScoreColors.blockLilac),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.music_notes_thin),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = WindScoreColors.ink,
                )
            }
            Spacer(Modifier.width(WindScoreSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    score.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W500),
                    color = WindScoreColors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${score.notes.size} 音符 · ${score.tempo} BPM",
                    style = MaterialTheme.typography.bodySmall,
                    color = WindScoreColors.ink,
                )
            }
        }
    }
}

@Composable
private fun PlayerScreen(
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
) {
    Column(Modifier.fillMaxSize()) {
        val sheetReady = viewMode == ViewMode.ROLL || renderMs != null
        PlayerToolbar(
            title = score.title,
            running = running,
            isCompact = isCompact,
            onBack = onBack,
            onToggleMidi = onToggleMidi,
            onReset = onReset,
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
                renderMs = renderMs,
                isCompact = isCompact,
                onRenderTime = onRenderTime,
                modifier = Modifier.weight(1f),
            )
        } else {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    MainStage(
                        session = session,
                        viewMode = viewMode,
                        playTick = playTick,
                        isPlaying = isPlaying,
                        playingPitches = playingPitches,
                        renderMs = renderMs,
                        isCompact = isCompact,
                        onRenderTime = onRenderTime,
                        modifier = Modifier.weight(1f),
                    )
                    StatusLine(session, viewMode, sheetReady)
                }
                ControlSidebar(
                    session = session,
                    practiceMode = practiceMode,
                    viewMode = viewMode,
                    pendingLoopStart = pendingLoopStart,
                    scoreHasSheet = score.rawXml != null,
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
                    tint = WindScoreColors.ink,
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
            CircularIconButton(onClick = onReset) {
                Icon(
                    painter = painterResource(Res.drawable.arrows_counter_clockwise_thin),
                    contentDescription = "重置",
                    modifier = Modifier.size(20.dp),
                    tint = WindScoreColors.ink,
                )
            }
            PillButton(style = if (running) PillButtonStyle.SECONDARY else PillButtonStyle.PRIMARY, onClick = onToggleMidi) {
                Text(if (running) "停止 MIDI" else "开始 MIDI")
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
                        tint = WindScoreColors.ink,
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
                            tint = WindScoreColors.ink,
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
                            tint = WindScoreColors.ink,
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
                        tint = if (sheetReady) WindScoreColors.ink else WindScoreColors.ink.copy(alpha = 0.4f),
                    )
                }
                CircularIconButton(onClick = onStopPlay, enabled = sheetReady) {
                    Icon(
                        painter = painterResource(Res.drawable.stop_thin),
                        contentDescription = "停止",
                        modifier = Modifier.size(20.dp),
                        tint = if (sheetReady) WindScoreColors.ink else WindScoreColors.ink.copy(alpha = 0.4f),
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
                        tint = if (session.isLoopActive) WindScoreColors.ink.copy(alpha = 0.4f) else WindScoreColors.ink,
                    )
                }
                if (pendingLoopStart != null && !session.isLoopActive) {
                    Text(
                        "起点 ${pendingLoopStart}",
                        style = MaterialTheme.typography.bodySmall,
                        color = WindScoreColors.ink,
                    )
                }
                CircularIconButton(onClick = onLoopEnd, enabled = !session.isLoopActive && pendingLoopStart != null) {
                    Icon(
                        painter = painterResource(Res.drawable.repeat_thin),
                        contentDescription = "设终点",
                        modifier = Modifier.size(20.dp),
                        tint = if (session.isLoopActive || pendingLoopStart == null) WindScoreColors.ink.copy(alpha = 0.4f) else WindScoreColors.ink,
                    )
                }
                CircularIconButton(onClick = onClearLoop, enabled = session.isLoopActive) {
                    Icon(
                        painter = painterResource(Res.drawable.x_thin),
                        contentDescription = "清除循环",
                        modifier = Modifier.size(20.dp),
                        tint = if (session.isLoopActive) WindScoreColors.ink else WindScoreColors.ink.copy(alpha = 0.4f),
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
        if (viewMode != ViewMode.ROLL && !sheetReady) {
            Text(
                "正在加载乐谱渲染…",
                style = MaterialTheme.typography.bodySmall,
                color = WindScoreColors.accentMagenta,
            )
        } else {
            val pending = session.pendingPitches.joinToString { pitchName(it) }
            Text(
                if (session.finished) "演奏完成 · 准度 ${session.scorePercent()}%" else "该弹: $pending",
                style = MaterialTheme.typography.bodySmall,
                color = if (session.finished) WindScoreColors.semanticSuccess else WindScoreColors.ink,
            )
        }
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
    renderMs: Double?,
    isCompact: Boolean,
    onRenderTime: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val frameH = if (isCompact) WindScoreSpacing.sm else WindScoreSpacing.xs
    val frameV = if (isCompact) WindScoreSpacing.sm else WindScoreSpacing.xs
    val innerPadding = if (isCompact) WindScoreSpacing.lg else WindScoreSpacing.sm
    val tick = if (isPlaying) playTick else session.currentTick
    val showRoll = viewMode == ViewMode.ROLL
    val showSheetKb = viewMode == ViewMode.SHEET_KEYBOARD
    val showSheetFull = viewMode == ViewMode.SHEET_FULL
    val sheetFraction = if (showSheetKb) 0.72f else 1f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = frameH, vertical = frameV)
            .clip(RoundedCornerShape(WindScoreRounded.lg))
            .background(WindScoreColors.blockLime),
    ) {
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            // Keep-Alive: 所有视图始终挂载，通过 alpha 控制可见性
            val rawXml = session.score.rawXml
            // 活跃音高：播放时用 ScorePlayer 的发音，练习时用 session
            val activePitches = if (isPlaying) playingPitches else session.heldPitches
            val targetPitches = if (isPlaying) playingPitches else session.pendingPitches

            // 卷帘 — 始终挂载
            PianoRoll(
                score = session.score,
                currentTickProvider = { tick },
                modifier = Modifier.fillMaxSize().alpha(if (showRoll) 1f else 0f),
                hostedTrack = session.hostedTrack,
                heldPitchesProvider = { activePitches },
                pulseIntensityProvider = { session.pulseIntensity },
                bpmProvider = { session.score.tempo.takeIf { it > 0 } ?: 120 },
            )
            // 五线谱 — 始终挂载（SwingPanel 需移出屏幕而非 alpha=0）
            val sheetVisible = showSheetKb || showSheetFull
            if (rawXml != null) {
                ScoreSheetView(
                    rawXml = rawXml,
                    currentTickProvider = { tick },
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(sheetFraction)
                        .then(if (sheetVisible) Modifier else Modifier.offset(x = 99999.dp)),
                    divisions = session.score.divisions,
                    onRenderTime = onRenderTime,
                )
            }
            // 静态键盘 — 模式二下半部分
            StaticKeyboard(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.28f)
                    .align(Alignment.BottomCenter)
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
    onModeChange: (PracticeMode) -> Unit,
    onViewChange: (ViewMode) -> Unit,
    onLoopStart: () -> Unit,
    onLoopEnd: () -> Unit,
    onClearLoop: () -> Unit,
    onTogglePlay: () -> Unit,
    onStopPlay: () -> Unit,
) {
    Surface(
        modifier = Modifier.width(200.dp).fillMaxHeight(),
        color = WindScoreColors.surfaceSoft,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(WindScoreSpacing.md),
            verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.sm),
        ) {
            Text("模式", style = MaterialTheme.typography.labelSmall, color = WindScoreColors.ink.copy(alpha = 0.5f))
            PillToggle(practiceMode == PracticeMode.BOTH, { onModeChange(PracticeMode.BOTH) }, "双手", modifier = Modifier.fillMaxWidth())
            PillToggle(practiceMode == PracticeMode.RIGHT_ONLY, { onModeChange(PracticeMode.RIGHT_ONLY) }, "右手", modifier = Modifier.fillMaxWidth())
            PillToggle(practiceMode == PracticeMode.LEFT_ONLY, { onModeChange(PracticeMode.LEFT_ONLY) }, "左手", modifier = Modifier.fillMaxWidth())

            Text("视图", style = MaterialTheme.typography.labelSmall, color = WindScoreColors.ink.copy(alpha = 0.5f))
            Row(horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)) {
                CircularIconButton(onClick = { onViewChange(ViewMode.ROLL) }, selected = viewMode == ViewMode.ROLL) {
                    Icon(painterResource(Res.drawable.piano_keys_thin), "卷帘", Modifier.size(20.dp), WindScoreColors.ink)
                }
                if (scoreHasSheet) {
                    CircularIconButton(onClick = { onViewChange(ViewMode.SHEET_KEYBOARD) }, selected = viewMode == ViewMode.SHEET_KEYBOARD) {
                        Icon(painterResource(Res.drawable.book_open_text_thin), "识谱", Modifier.size(20.dp), WindScoreColors.ink)
                    }
                    CircularIconButton(onClick = { onViewChange(ViewMode.SHEET_FULL) }, selected = viewMode == ViewMode.SHEET_FULL) {
                        Icon(painterResource(Res.drawable.music_notes_thin), "看谱", Modifier.size(20.dp), WindScoreColors.ink)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)) {
                CircularIconButton(onClick = onTogglePlay, enabled = sheetReady) {
                    Icon(
                        painterResource(if (isPlaying) Res.drawable.pause_thin else Res.drawable.play_thin),
                        if (isPlaying) "暂停" else "播放", Modifier.size(20.dp),
                        if (sheetReady) WindScoreColors.ink else WindScoreColors.ink.copy(alpha = 0.4f),
                    )
                }
                CircularIconButton(onClick = onStopPlay, enabled = sheetReady) {
                    Icon(
                        painterResource(Res.drawable.stop_thin), "停止", Modifier.size(20.dp),
                        if (sheetReady) WindScoreColors.ink else WindScoreColors.ink.copy(alpha = 0.4f),
                    )
                }
                Text(
                    "${(session.currentTick * 100 / (session.score.totalTicks.coerceAtLeast(1))).coerceIn(0, 100)}%",
                    style = MaterialTheme.typography.bodySmall, color = WindScoreColors.ink,
                )
            }

            Text("循环", style = MaterialTheme.typography.labelSmall, color = WindScoreColors.ink.copy(alpha = 0.5f))
            Row(horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xxs)) {
                CircularIconButton(onClick = onLoopStart, enabled = !session.isLoopActive) {
                    Icon(painterResource(Res.drawable.repeat_thin), "起点", Modifier.size(20.dp),
                        if (session.isLoopActive) WindScoreColors.ink.copy(alpha = 0.4f) else WindScoreColors.ink)
                }
                CircularIconButton(onClick = onLoopEnd, enabled = !session.isLoopActive && pendingLoopStart != null) {
                    Icon(painterResource(Res.drawable.repeat_thin), "终点", Modifier.size(20.dp),
                        if (session.isLoopActive || pendingLoopStart == null) WindScoreColors.ink.copy(alpha = 0.4f) else WindScoreColors.ink)
                }
                CircularIconButton(onClick = onClearLoop, enabled = session.isLoopActive) {
                    Icon(painterResource(Res.drawable.x_thin), "清除", Modifier.size(20.dp),
                        if (session.isLoopActive) WindScoreColors.ink else WindScoreColors.ink.copy(alpha = 0.4f))
                }
            }
            if (session.isLoopActive) {
                Text("循环 ${session.loopRange.first}~${session.loopRange.last}",
                    style = MaterialTheme.typography.bodySmall, color = WindScoreColors.accentMagenta)
            } else if (pendingLoopStart != null) {
                Text("起点 ${pendingLoopStart}", style = MaterialTheme.typography.bodySmall, color = WindScoreColors.ink)
            }
        }
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
    ) {
        Text(label)
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
