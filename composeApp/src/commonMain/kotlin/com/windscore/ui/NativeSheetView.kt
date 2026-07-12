@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.windscore.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.roundToInt

private val CURSOR_COLOR = Color(0xFF4A90D9).copy(alpha = 0.6f)

@Composable
fun NativeSheetView(
    snapshot: SheetSnapshot,
    currentTickProvider: () -> Long,
    onSeek: (Long) -> Unit,
    onStepForward: () -> Unit,
    onStepBackward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scrollY by remember { mutableFloatStateOf(0f) }

    val chunks = snapshot.chunks
    val srcW = snapshot.chunkWidth
    val chunkH = snapshot.chunkHeight
    val totalH = snapshot.totalHeight
    val steps = snapshot.cursorSteps

    Canvas(
        modifier
            .fillMaxSize()
            .clip(RectangleShape)
            // 鼠标滚轮：竖向滚动（平台 expect/actual）
            .scrollWheelHandler { dy ->
                scrollY = (scrollY + dy * 50f)
                    .coerceIn(0f, totalH.toFloat())
            }
            // 点击：吸附到最近音符
            .pointerInput(snapshot) {
                detectTapGestures { offset ->
                    val scale = size.width.toFloat() / srcW
                    val absY = offset.y / scale + scrollY
                    val absX = offset.x / scale
                    val nearest = steps.minByOrNull { s ->
                        abs(s.y - absY) * 3f + abs(s.x - absX)
                    }
                    nearest?.let { onSeek(it.tick) }
                }
            }
            // 拖拽：竖向滚动 + 横向步进
            .pointerInput(snapshot) {
                var anchorX = 0f
                detectDragGestures(
                    onDragStart = { offset -> anchorX = offset.x },
                    onDrag = { change, dragAmount ->
                        if (abs(dragAmount.y) >= abs(dragAmount.x)) {
                            val scale = size.width.toFloat() / srcW
                            scrollY = (scrollY - dragAmount.y / scale)
                                .coerceIn(0f, (totalH - size.height / scale).coerceAtLeast(0f))
                        } else {
                            val dx = change.position.x - anchorX
                            if (dx > 40f) { onStepForward(); anchorX = change.position.x }
                            else if (dx < -40f) { onStepBackward(); anchorX = change.position.x }
                        }
                    },
                )
            },
    ) {
        val viewW = size.width
        val viewH = size.height
        val scale = viewW / srcW.toFloat()
        val viewHInSrc = viewH / scale

        // 光标自动追焦：光标进入视口顶部 20% 或底部 65% 时触发滚动
        val tick = currentTickProvider()
        val step = findCursorStep(tick, steps)
        if (step != null) {
            val cursorRawY = step.y - scrollY
            if (cursorRawY < viewHInSrc * 0.15f || cursorRawY > viewHInSrc * 0.5f) {
                scrollY = (step.y - viewHInSrc * 0.2f)
                    .coerceIn(0f, (totalH - viewHInSrc).coerceAtLeast(0f))
            }
        }

        // 分块绘制
        chunks.forEachIndexed { i, chunk ->
            val yDst = ((i * chunkH) - scrollY) * scale
            val hDst = chunk.height * scale
            if (yDst < viewH && yDst + hDst > 0) {
                drawImage(
                    image = chunk,
                    dstOffset = IntOffset(0, yDst.roundToInt()),
                    dstSize = IntSize(viewW.roundToInt(), hDst.roundToInt()),
                )
            }
        }

        // 底部空白
        val contentBottom = (totalH - scrollY) * scale
        if (contentBottom < viewH) {
            drawRect(Color.White, Offset(0f, contentBottom), Size(viewW, viewH - contentBottom))
        }

        // 光标
        if (step != null) {
            val cursorY = (step.y - scrollY) * scale
            if (cursorY >= -step.height * scale && cursorY <= viewH) {
                drawRect(
                    CURSOR_COLOR,
                    Offset(step.x * scale, cursorY),
                    Size(4f * scale, step.height * scale),
                )
            }
        }
    }
}
