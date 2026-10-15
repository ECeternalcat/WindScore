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
import com.windscore.core.engine.ScoreSession
import com.windscore.core.model.PracticeMode
import com.windscore.ui.components.PillButton
import com.windscore.ui.components.PillButtonStyle
import com.windscore.ui.components.PillToggle
import com.windscore.ui.components.CustomSwitch
import com.windscore.ui.components.CircularIconButton
import com.windscore.ui.i18n.strings
import com.windscore.ui.theme.windScoreColors
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
import windscore.composeapp.generated.resources.clock_thin

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
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
    onToggleMidi: () -> Unit,
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
    activeLoopRange: LongRange? = null,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WindScoreRounded.lg))
            .background(windScoreColors().canvas)
            .border(1.dp, windScoreColors().hairline, RoundedCornerShape(WindScoreRounded.lg))
            .padding(WindScoreSpacing.md),
        verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.md),
    ) {
        // Top: 启动midi接入 (Start MIDI connection)
        PillButton(
            style = if (running) PillButtonStyle.SECONDARY else PillButtonStyle.PRIMARY,
            onClick = onToggleMidi,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (running) s.disconnectMidi else s.connectMidi)
        }

        // Group 1: 模式 (Mode)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, windScoreColors().hairlineSoft, RoundedCornerShape(WindScoreRounded.md))
                .padding(WindScoreSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)
        ) {
            Text(s.mode, style = MaterialTheme.typography.labelSmall, color = windScoreColors().ink.copy(alpha = 0.5f))
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)
            ) {
                PillToggle(practiceMode == PracticeMode.BOTH, { onModeChange(PracticeMode.BOTH) }, s.bothHands, modifier = Modifier.weight(1f))
                PillToggle(practiceMode == PracticeMode.RIGHT_ONLY, { onModeChange(PracticeMode.RIGHT_ONLY) }, s.rightHand, modifier = Modifier.weight(1f))
                PillToggle(practiceMode == PracticeMode.LEFT_ONLY, { onModeChange(PracticeMode.LEFT_ONLY) }, s.leftHand, modifier = Modifier.weight(1f))
            }
        }

        // Group 2: 视图与播放 (View & Play)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, windScoreColors().hairlineSoft, RoundedCornerShape(WindScoreRounded.md))
                .padding(WindScoreSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.md)
            ) {
                // Left: View Mode Group
                Column(modifier = Modifier.weight(3f)) {
                    Text(s.view, style = MaterialTheme.typography.labelSmall, color = windScoreColors().ink.copy(alpha = 0.5f))
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)) {
                        ViewModeButton(ViewMode.ROLL, viewMode == ViewMode.ROLL) { onViewChange(ViewMode.ROLL) }
                        if (scoreHasSheet) {
                            ViewModeButton(ViewMode.SHEET_KEYBOARD, viewMode == ViewMode.SHEET_KEYBOARD) { onViewChange(ViewMode.SHEET_KEYBOARD) }
                            ViewModeButton(ViewMode.SHEET_FULL, viewMode == ViewMode.SHEET_FULL) { onViewChange(ViewMode.SHEET_FULL) }
                        }
                    }
                }

                // Right: Playback Control Group
                Column(modifier = Modifier.weight(2f)) {
                    Text(s.playback, style = MaterialTheme.typography.labelSmall, color = windScoreColors().ink.copy(alpha = 0.5f))
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)) {
                        CircularIconButton(onClick = onTogglePlay, enabled = sheetReady) {
                            Icon(
                                painterResource(if (isPlaying) Res.drawable.pause_thin else Res.drawable.play_thin),
                                if (isPlaying) s.pause else s.play, Modifier.size(20.dp),
                            )
                        }
                        CircularIconButton(onClick = onStopPlay, enabled = sheetReady) {
                            Icon(
                                painterResource(Res.drawable.stop_thin), s.stop, Modifier.size(20.dp),
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
                .border(1.dp, windScoreColors().hairlineSoft, RoundedCornerShape(WindScoreRounded.md))
                .padding(WindScoreSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(s.speed, style = MaterialTheme.typography.labelSmall, color = windScoreColors().ink.copy(alpha = 0.5f))
                Text(
                    text = "${(playbackSpeed * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
                    color = windScoreColors().ink
                )
            }

            // Slider (拖动条)
            androidx.compose.material3.Slider(
                value = playbackSpeed,
                onValueChange = { newValue ->
                    val speedVal = newValue.coerceIn(0.0f, 2.0f)
                    onPlaybackSpeedChange(speedVal)
                },
                valueRange = 0.0f..2.0f,
                modifier = Modifier.fillMaxWidth().height(24.dp),
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = windScoreColors().primary,
                    activeTrackColor = windScoreColors().primary,
                    inactiveTrackColor = windScoreColors().hairlineSoft
                )
            )

            // Speed Presets Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)
            ) {
                listOf(0.5f to "x0.5", 1.0f to s.originalSpeed, 1.5f to "x1.5").forEach { (value, label) ->
                    val isSelected = playbackSpeed == value
                    PillToggle(
                        selected = isSelected,
                        onClick = {
                            onPlaybackSpeedChange(value)
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
                .border(1.dp, windScoreColors().hairlineSoft, RoundedCornerShape(WindScoreRounded.md))
                .padding(WindScoreSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)
        ) {
            Text(s.loop, style = MaterialTheme.typography.labelSmall, color = windScoreColors().ink.copy(alpha = 0.5f))
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Loop Start
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xxs)) {
                    CircularIconButton(
                        onClick = onLoopStart,
                        selected = pendingLoopStart != null || activeLoopRange != null,
                        testTag = "loop-start",
                    ) {
                        Icon(painterResource(Res.drawable.repeat_thin), s.loopStart, Modifier.size(20.dp))
                    }
                    Text(s.loopStart, style = MaterialTheme.typography.bodySmall, color = windScoreColors().ink)
                }

                // Loop End
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xxs)) {
                    CircularIconButton(
                        onClick = onLoopEnd,
                        enabled = pendingLoopStart != null || activeLoopRange != null,
                        selected = activeLoopRange != null,
                        testTag = "loop-end",
                    ) {
                        Icon(painterResource(Res.drawable.repeat_thin), s.loopEnd, Modifier.size(20.dp))
                    }
                    Text(s.loopEnd, style = MaterialTheme.typography.bodySmall, color = windScoreColors().ink)
                }

                // Clear
                CircularIconButton(
                    onClick = onClearLoop,
                    enabled = activeLoopRange != null || pendingLoopStart != null,
                    testTag = "loop-clear",
                ) {
                    Icon(painterResource(Res.drawable.x_thin), s.clear, Modifier.size(20.dp))
                }
            }
        }

        // Group 4: 节拍器 (Metronome)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, windScoreColors().hairlineSoft, RoundedCornerShape(WindScoreRounded.md))
                .padding(WindScoreSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(s.metronome, style = MaterialTheme.typography.labelSmall, color = windScoreColors().ink.copy(alpha = 0.5f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)
                ) {
                    Text(s.countdownBeat, style = MaterialTheme.typography.bodySmall, color = windScoreColors().ink.copy(alpha = 0.5f))
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
                    contentDescription = s.metronomeVolume,
                    modifier = Modifier.size(20.dp).clip(RoundedCornerShape(WindScoreRounded.full))
                        .clickable(onClick = onToggleMute),
                    tint = windScoreColors().ink.copy(alpha = 0.5f)
                )
                androidx.compose.material3.Slider(
                    value = metronomeVol.toFloat(),
                    onValueChange = { newValue ->
                        onMetronomeVolChange(newValue.toInt())
                    },
                    valueRange = 0.0f..100.0f,
                    modifier = Modifier.weight(1f).height(24.dp),
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = windScoreColors().primary,
                        activeTrackColor = windScoreColors().primary,
                        inactiveTrackColor = windScoreColors().hairlineSoft
                    )
                )
                Text(
                    text = "${metronomeVol}%",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
                    color = windScoreColors().ink,
                    modifier = Modifier.width(44.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
        }
    }
}
