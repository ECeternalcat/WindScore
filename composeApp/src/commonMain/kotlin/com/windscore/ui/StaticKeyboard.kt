package com.windscore.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.windscore.ui.theme.WindScoreColors

private val BLACK_KEY_OFFSETS = setOf(1, 3, 6, 8, 10)

/**
 * 静态 88 键键盘（模式二下方 Weight 0.25）。
 *
 * - pendingPitches: 当前待弹音高 → 外框高亮
 * - heldPitches: 物理按下的音高 → 填充实心
 */
@Composable
fun StaticKeyboard(
    modifier: Modifier = Modifier,
    pitchLow: Int = 21,
    pitchHigh: Int = 108,
    pendingPitchesProvider: () -> Set<Int> = { emptySet() },
    heldPitchesProvider: () -> Set<Int> = { emptySet() },
) {
    Canvas(modifier.fillMaxSize()) {
        val pending = pendingPitchesProvider()
        val held = heldPitchesProvider()
        drawKeyboard(pitchLow, pitchHigh, pending, held, size.width, size.height)
    }
}

private fun DrawScope.drawKeyboard(
    pitchLow: Int,
    pitchHigh: Int,
    pending: Set<Int>,
    held: Set<Int>,
    w: Float,
    h: Float,
) {
    val keyCount = (pitchHigh - pitchLow + 1).toFloat()
    val keyWidth = w / keyCount
    val stroke = Stroke(width = 3f)

    for (pitch in pitchLow..pitchHigh) {
        val x = (pitch - pitchLow) * keyWidth
        val isBlack = pitch % 12 in BLACK_KEY_OFFSETS
        val isPending = pitch in pending
        val isHeld = pitch in held

        if (isBlack) {
            // 黑键
            val color = when {
                isHeld -> WindScoreColors.accentMagenta
                else -> WindScoreColors.inverseCanvas
            }
            drawRect(
                color = color,
                topLeft = Offset(x + keyWidth * 0.2f, h * 0.1f),
                size = Size(keyWidth * 0.6f, h * 0.8f),
            )
        } else {
            // 白键
            val bg = WindScoreColors.canvas
            drawRect(color = bg, topLeft = Offset(x, 0f), size = Size(keyWidth, h))
            // 白键分隔线
            drawLine(
                color = WindScoreColors.hairline,
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 0.8f,
            )
            // 按下填充
            if (isHeld) {
                drawRect(
                    color = WindScoreColors.accentMagenta.copy(alpha = 0.6f),
                    topLeft = Offset(x + keyWidth * 0.05f, h * 0.1f),
                    size = Size(keyWidth * 0.9f, h * 0.8f),
                )
            }
            // 待弹外框
            if (isPending) {
                drawRect(
                    color = WindScoreColors.accentMagenta,
                    topLeft = Offset(x + keyWidth * 0.08f, h * 0.06f),
                    size = Size(keyWidth * 0.84f, h * 0.88f),
                    style = stroke,
                )
            }
        }
    }
    // 顶部线
    drawLine(color = WindScoreColors.hairline, start = Offset(0f, 0f), end = Offset(w, 0f), strokeWidth = 1f)
    // C 键定位标记：每个八度的 C 用淡点，中央 C（MIDI 60）用品红实心点突出
    for (pitch in pitchLow..pitchHigh) {
        if (pitch % 12 != 0) continue
        val cx = (pitch - pitchLow) * keyWidth + keyWidth / 2f
        if (pitch == 60) {
            drawCircle(
                color = WindScoreColors.accentMagenta,
                radius = (keyWidth * 0.17f).coerceAtLeast(3.5f),
                center = Offset(cx, h * 0.95f),
            )
        } else {
            drawCircle(
                color = WindScoreColors.ink.copy(alpha = 0.28f),
                radius = (keyWidth * 0.1f).coerceAtLeast(2f),
                center = Offset(cx, h * 0.95f),
            )
        }
    }
}
