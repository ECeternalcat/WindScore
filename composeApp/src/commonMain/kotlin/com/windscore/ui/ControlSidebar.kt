package com.windscore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.windscore.ViewMode
import com.windscore.core.engine.ScorePlayer
import com.windscore.core.engine.ScoreSession
import com.windscore.core.model.PracticeMode
import com.windscore.ui.components.PillButton
import com.windscore.ui.components.PillButtonStyle
import com.windscore.ui.components.PillToggle
import com.windscore.ui.components.CustomSwitch
import com.windscore.ui.components.CircularIconButton
import com.windscore.ui.theme.WindScoreColors
import com.windscore.ui.theme.WindScoreRounded
import com.windscore.ui.theme.WindScoreSpacing
import org.jetbrains.compose.resources.painterResource
import windscore.composeapp.generated.resources.Res
import windscore.composeapp.generated.resources.arrows_counter_clockwise_thin
import windscore.composeapp.generated.resources.piano_keys_thin
import windscore.composeapp.generated.resources.book_open_text_thin
import windscore.composeapp.generated.resources.music_notes_thin
import windscore.composeapp.generated.resources.pause_thin
import windscore.composeapp.generated.resources.play_thin
import windscore.composeapp.generated.resources.stop_thin
import windscore.composeapp.generated.resources.repeat_thin
import windscore.composeapp.generated.resources.x_thin
import windscore.composeapp.generated.resources.speaker_high_thin
import windscore.composeapp.generated.resources.speaker_slash_thin

@Composable
fun ControlSidebar(
    session: ScoreSession,
    practiceMode: PracticeMode,
    viewMode: ViewMode,
    pendingLoopStart: Long?,
    scoreHasSheet: Boolean,
    isPlaying: Boolean,
    sheetReady: Boolean,
    running: Boolean,
    player: ScorePlayer?,
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
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
                    text = "${(playbackSpeed * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
                    color = WindScoreColors.ink
                )
            }

            // Slider (拖动条)
            androidx.compose.material3.Slider(
                value = playbackSpeed,
                onValueChange = { newValue ->
                    val speedVal = newValue.coerceIn(0.0f, 2.0f)
                    onPlaybackSpeedChange(speedVal)
                    player?.speedMultiplier = speedVal.toDouble()
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
                    val isSelected = playbackSpeed == value
                    PillToggle(
                        selected = isSelected,
                        onClick = {
                            onPlaybackSpeedChange(value)
                            player?.speedMultiplier = value.toDouble()
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
                    CircularIconButton(
                        onClick = {
                            if (session.isLoopActive) onClearLoop()
                            onLoopStart()
                        }, 
                        selected = pendingLoopStart != null || session.isLoopActive
                    ) {
                        Icon(painterResource(Res.drawable.repeat_thin), "起点", Modifier.size(20.dp))
                    }
                    Text("起点", style = MaterialTheme.typography.bodySmall, color = WindScoreColors.ink)
                }

                // Loop End
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xxs)) {
                    CircularIconButton(
                        onClick = onLoopEnd, 
                        enabled = pendingLoopStart != null || session.isLoopActive,
                        selected = session.isLoopActive
                    ) {
                        Icon(painterResource(Res.drawable.repeat_thin), "终点", Modifier.size(20.dp))
                    }
                    Text("终点", style = MaterialTheme.typography.bodySmall, color = WindScoreColors.ink)
                }

                // Clear
                CircularIconButton(
                    onClick = onClearLoop, 
                    enabled = session.isLoopActive || pendingLoopStart != null
                ) {
                    Icon(painterResource(Res.drawable.x_thin), "清除", Modifier.size(20.dp))
                }
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
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
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
