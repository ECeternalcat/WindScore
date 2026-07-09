package com.windscore.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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

private val CLEAN = Color(0xFF3DBB6C)   // 干净（冷绿）
private val STUCK = Color(0xFFE0A33A)   // 卡壳（暖琥珀，避免红色）

/**
 * 结算页（文档 4.3）：
 * - 仅在一曲结束后出现；过程绝不打叉/飘红/扣分。
 * - 显示准度分数 + 温和的“卡壳热力图”（按组聚合的错按次数）。
 */
@Composable
fun ResultScreen(
    session: ScoreSession,
    modifier: Modifier = Modifier,
    onReset: () -> Unit,
) {
    val percent = session.scorePercent()
    val clean = session.cleanHitCount()
    val total = session.totalActiveNotes()
    val wrong = session.wrongPressCount()
    val heatmap = session.groupWrongCounts()

    Surface(
        modifier = modifier.padding(16.dp),
        color = Color(0xFF1F2229).copy(alpha = 0.96f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("演奏完成", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                "$percent%",
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = if (percent >= 80) CLEAN else STUCK,
            )
            Text(
                "干净命中 $clean / $total   ·   错按 $wrong 次",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFC8CCD3),
            )
            Spacer(Modifier.height(16.dp))
            Text("卡壳热力图", style = MaterialTheme.typography.titleSmall, color = Color(0xFFC8CCD3))
            Text("（颜色越暖表示该处越需要多练）", fontSize = 11.sp, color = Color(0xFF8A8F99))
            Spacer(Modifier.height(6.dp))
            Heatmap(heatmap, Modifier.fillMaxWidth().height(40.dp))
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onReset) { Text("再来一次") }
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
