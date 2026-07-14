package com.windscore.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.windscore.core.engine.ScoreSession
import com.windscore.ui.components.PillButton
import com.windscore.ui.components.PillButtonStyle
import com.windscore.ui.i18n.strings
import com.windscore.ui.theme.WindScoreColors
import com.windscore.ui.theme.WindScoreRounded
import com.windscore.ui.theme.WindScoreSpacing

private val CLEAN = Color(0xFF3DBB6C)   // 干净（冷绿）
private val STUCK = Color(0xFFE0A33A)   // 卡壳（暖琥珀，避免红色）

/**
 * 结算页（文档 4.3）：
 * - 仅在一曲结束后出现；过程绝不打叉/飘红/扣分。
 * - 显示准度分数 + 温和的“卡壳热力图”（按组聚合的错按次数）。
 * - UI 适配 DESIGN.md：白底卡片 + pastel 色块背景 + pill 按钮。
 */
@Composable
fun ResultScreen(
    session: ScoreSession,
    modifier: Modifier = Modifier,
    onReset: () -> Unit,
) {
    val s = strings()
    val percent = session.scorePercent()
    val clean = session.cleanHitCount()
    val total = session.totalActiveNotes()
    val wrong = session.wrongPressCount()
    val heatmap = session.groupWrongCounts()

    Surface(
        modifier = modifier.padding(WindScoreSpacing.lg),
        color = WindScoreColors.blockCream,
        shape = RoundedCornerShape(WindScoreRounded.lg),
    ) {
        Column(
            modifier = Modifier.padding(WindScoreSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                s.performanceComplete,
                style = MaterialTheme.typography.headlineLarge,
                color = WindScoreColors.ink,
            )
            Spacer(Modifier.height(WindScoreSpacing.md))
            Text(
                "$percent%",
                fontSize = 64.sp,
                fontWeight = FontWeight.W300,
                color = if (percent >= 80) CLEAN else STUCK,
                letterSpacing = (-0.96).sp,
            )
            Text(
                s.cleanHitsLine(clean, total, wrong),
                style = MaterialTheme.typography.bodyMedium,
                color = WindScoreColors.ink.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(WindScoreSpacing.xl))
            Text(
                s.stuckHeatmap,
                style = MaterialTheme.typography.titleMedium,
                color = WindScoreColors.ink,
            )
            Text(
                s.heatmapHint,
                style = MaterialTheme.typography.bodySmall,
                color = WindScoreColors.ink.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(WindScoreSpacing.sm))
            Heatmap(heatmap, Modifier.fillMaxWidth().height(40.dp))
            Spacer(Modifier.height(WindScoreSpacing.xl))
            Row(horizontalArrangement = Arrangement.spacedBy(WindScoreSpacing.sm)) {
                PillButton(style = PillButtonStyle.PRIMARY, onClick = onReset) { Text(s.tryAgain) }
            }
        }
    }
}

@Composable
private fun Heatmap(counts: IntArray, modifier: Modifier = Modifier) {
    val maxCount = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (counts.isEmpty()) return@Canvas
        val cellW = w / counts.size
        for (i in counts.indices) {
            val ratio = counts[i].toFloat() / maxCount
            val color = lerp(CLEAN, STUCK, ratio)
            drawRect(
                color = color,
                topLeft = Offset(i * cellW, 0f),
                size = Size(cellW.coerceAtLeast(1f) - 1f, h),
            )
        }
    }
}
