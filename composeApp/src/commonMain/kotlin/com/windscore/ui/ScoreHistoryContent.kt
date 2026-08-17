package com.windscore.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.windscore.core.formatDateTime
import com.windscore.core.model.PracticeMode
import com.windscore.core.model.ScoreRecord
import com.windscore.ui.components.CircularIconButton
import com.windscore.ui.i18n.strings
import com.windscore.ui.theme.windScoreColors
import com.windscore.ui.theme.WindScoreRounded
import com.windscore.ui.theme.WindScoreSpacing
import org.jetbrains.compose.resources.painterResource
import windscore.composeapp.generated.resources.Res
import windscore.composeapp.generated.resources.arrow_left_thin
import windscore.composeapp.generated.resources.clock_thin

/**
 * 评分历史对话框内容：标题栏 + 趋势图 + 记录列表。
 * 每条记录上方显示日期/时间与模式，主体显示分数与“较上次”的变化；点击展开查看 正确/错误/总数 细节。
 */
@Composable
fun ScoreHistoryContent(
    records: List<ScoreRecord>,
    onDismiss: () -> Unit,
) {
    val s = strings()
    Column(
        modifier = Modifier.fillMaxSize().background(windScoreColors().canvas),
    ) {
        // 标题栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(WindScoreSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularIconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(Res.drawable.arrow_left_thin),
                    contentDescription = s.back,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.size(WindScoreSpacing.sm))
            Text(
                s.scoreHistory,
                style = MaterialTheme.typography.headlineMedium,
                color = windScoreColors().ink,
            )
        }

        if (records.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    s.noHistory,
                    style = MaterialTheme.typography.bodyMedium,
                    color = windScoreColors().ink.copy(alpha = 0.5f),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = WindScoreSpacing.lg, vertical = WindScoreSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.sm),
            ) {
                // 趋势图（至少 2 条记录才有意义）
                if (records.size >= 2) {
                    item(key = "trend") { ScoreTrendChart(records) }
                }
                // records 为最新在前；index+1 即为“上一次（更旧）”的记录
                itemsIndexed(records, key = { _, r -> r.timestamp }) { index, record ->
                    val delta = if (index < records.size - 1) {
                        record.percent - records[index + 1].percent
                    } else {
                        null
                    }
                    HistoryItem(record, delta)
                }
            }
        }
    }
}

/** 分数随时间的迷你折线图（旧→新，左→右）。颜色按整体趋势：提升用绿，下降用琥珀。 */
@Composable
private fun ScoreTrendChart(records: List<ScoreRecord>) {
    val series = records.asReversed().map { it.percent.toFloat() } // oldest -> newest
    val improving = series.last() >= series.first()
    val color = if (improving) windScoreColors().scoreClean else windScoreColors().scoreStuck
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = windScoreColors().surfaceSoft,
        shape = RoundedCornerShape(WindScoreRounded.md),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(64.dp).padding(WindScoreSpacing.md)) {
            val n = series.size
            if (n < 2) return@Canvas
            val stepX = size.width / (n - 1)
            val pts = series.mapIndexed { i, v ->
                Offset(i * stepX, size.height - (v / 100f) * size.height)
            }
            for (i in 1 until pts.size) {
                drawLine(color = color, start = pts[i - 1], end = pts[i], strokeWidth = 3f)
            }
            pts.forEach { drawCircle(color = color, radius = 4f, center = it) }
        }
    }
}

@Composable
private fun HistoryItem(record: ScoreRecord, delta: Int?) {
    val s = strings()
    var expanded by remember { mutableStateOf(false) }
    val scoreColor = if (record.percent >= 80) windScoreColors().scoreClean else windScoreColors().scoreStuck
    val modeLabel = when (record.practiceMode) {
        PracticeMode.BOTH -> s.bothHands
        PracticeMode.RIGHT_ONLY -> s.rightHand
        PracticeMode.LEFT_ONLY -> s.leftHand
    }

    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(WindScoreRounded.md)).clickable { expanded = !expanded },
        color = windScoreColors().surfaceSoft,
        shape = RoundedCornerShape(WindScoreRounded.md),
    ) {
        Column(modifier = Modifier.padding(WindScoreSpacing.md)) {
            // 上方：日期/时间 + 模式
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs)) {
                    Icon(
                        painter = painterResource(Res.drawable.clock_thin),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = windScoreColors().ink.copy(alpha = 0.5f),
                    )
                    Text(
                        formatDateTime(record.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = windScoreColors().ink.copy(alpha = 0.6f),
                    )
                }
                Text(
                    modeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = windScoreColors().ink.copy(alpha = 0.5f),
                )
            }

            Spacer(Modifier.height(WindScoreSpacing.xs))

            // 主体：分数 + 较上次的变化
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.sm)) {
                Text(
                    "${record.percent}%",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.W300,
                    color = scoreColor,
                )
                DeltaChip(delta)
            }

            // 细节：正确 / 错误 / 总数
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = WindScoreSpacing.sm)
                        .clip(RoundedCornerShape(WindScoreRounded.sm))
                        .background(windScoreColors().canvas)
                        .border(1.dp, windScoreColors().hairlineSoft, RoundedCornerShape(WindScoreRounded.sm))
                        .padding(WindScoreSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(WindScoreSpacing.xs),
                ) {
                    DetailRow(s.correctLabel, "${record.cleanHits} / ${record.totalNotes}")
                    DetailRow(s.wrongLabel, "${record.wrongPresses}")
                    DetailRow(s.totalLabel, "${record.totalNotes}")
                }
            }
        }
    }
}

/** “较上次”的变化徽标：提升为绿、下降为琥珀、持平为灰；最旧一条（无上次）不显示。 */
@Composable
private fun DeltaChip(delta: Int?) {
    if (delta == null) return
    val (text, color) = when {
        delta > 0 -> "+$delta" to windScoreColors().scoreClean
        delta < 0 -> delta.toString() to windScoreColors().scoreStuck
        else -> "±0" to windScoreColors().ink.copy(alpha = 0.4f)
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(WindScoreRounded.pill),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
            color = color,
            modifier = Modifier.padding(horizontal = WindScoreSpacing.xs, vertical = 2.dp),
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = windScoreColors().ink.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W500), color = windScoreColors().ink)
    }
}
