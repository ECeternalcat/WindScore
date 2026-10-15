package com.windscore.ui

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 预渲染快照：OSMD 渲染结果分块截图 + 光标坐标表。
 * 分块规避 GPU 纹理尺寸限制（每块 ≤ 4000px 高）。
 */
data class SheetSnapshot(
    val chunks: List<ImageBitmap>,
    val chunkWidth: Int,
    val chunkHeight: Int,
    val totalHeight: Int,
    val cursorSteps: List<CursorStep>,
    val noteGeometry: NoteGeometrySnapshot? = null,
) {
    val hasValidGeometry: Boolean
        get() = chunks.isNotEmpty() && chunkWidth > 0 && chunkHeight > 0 && totalHeight > 0
}

/**
 * 光标步进：某个 tick 对应的光标位置（相对于位图的像素坐标）。
 */
data class CursorStep(
    val tick: Long,
    val x: Float,
    val y: Float,
    val height: Float,
)

/**
 * 在光标步进表中二分查找 tick 对应的位置。
 */
fun findCursorStep(tick: Long, steps: List<CursorStep>): CursorStep? {
    if (steps.isEmpty()) return null
    var lo = 0
    var hi = steps.size - 1
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (steps[mid].tick <= tick) lo = mid else hi = mid - 1
    }
    return steps[lo]
}
