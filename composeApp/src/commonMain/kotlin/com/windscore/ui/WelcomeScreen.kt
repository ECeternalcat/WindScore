package com.windscore.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.windscore.core.model.Score
import com.windscore.ui.components.PillButton
import com.windscore.ui.components.PillButtonStyle
import com.windscore.ui.theme.WindScoreColors
import com.windscore.ui.theme.WindScoreRounded
import com.windscore.ui.theme.WindScoreSpacing
import org.jetbrains.compose.resources.painterResource
import windscore.composeapp.generated.resources.Res
import windscore.composeapp.generated.resources.plus_thin
import windscore.composeapp.generated.resources.clock_thin
import windscore.composeapp.generated.resources.music_notes_thin

@Composable
fun WelcomeScreen(
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
