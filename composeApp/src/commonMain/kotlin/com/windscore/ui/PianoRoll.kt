package com.windscore.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.windscore.core.model.NoteEntity
import com.windscore.core.model.Score
import com.windscore.core.model.TrackType

// 冷色系（左手）
private val LEFT_HIT = Color(0xFF2E8BFF)
private val LEFT_UNHIT = Color(0xFF3A6FB0)
// 暖色系（右手）
private val RIGHT_HIT = Color(0xFFFF8C00)
private val RIGHT_UNHIT = Color(0xFFC97A2E)

private val BG_TOP = Color(0xFF1B1D22)
private val BG_BOTTOM = Color(0xFF0F1115)
private val HIT_LINE = Color(0xFFE6E6E6)
private val WHITE_KEY = Color(0xFFEAEAEA)
private val BLACK_KEY = Color(0xFF2A2D33)
private val GRID = Color(0x22FFFFFF)

private val BLACK_KEY_OFFSETS = setOf(1, 3, 6, 8, 10)

/**
 * 钢琴卷帘（Piano Roll）。纯 Compose Canvas 绘制，避免用 Column/Row 堆叠导致的重组风暴。
 *
 * 几何约定：
 * - Y 轴 = 时间。音符从上往下掉落，判定线（hitLineY）靠近底部。
 * - 音符的前沿（按下时刻 startTick）随 [currentTickProvider] 推进向判定线移动并越过。
 * - X 轴 = 音高。按屏幕宽度动态等分 [pitchLow..pitchHigh]（文档 3.4 长宽比适配）。
 *
 * 性能：[currentTickProvider] 在 draw 阶段被调用，因此高频 tick 变化只触发重绘、不触发重组；
 * 只绘制与视口相交的音符（视口裁剪）。
 *
 * @param lookaheadBeats 预览窗口（拍数）：一个音符从屏幕顶部落到判定线所需的拍数
 */
@Composable
fun PianoRoll(
    score: Score,
    currentTickProvider: () -> Long,
    modifier: Modifier = Modifier,
    lookaheadBeats: Float = 4f,
    hostedTrack: TrackType? = null,
    heldPitchesProvider: () -> Set<Int> = { emptySet() },
) {
    if (score.notes.isEmpty()) {
        Box(modifier.fillMaxSize())
        return
    }

    val pitchMin = score.notes.minOf { it.pitch }
    val pitchMax = score.notes.maxOf { it.pitch }
    val pitchLow = (pitchMin - 2).coerceIn(21, 108)
    val pitchHigh = (pitchMax + 2).coerceIn(21, 108)

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val tick = currentTickProvider()
        val held = heldPitchesProvider()

        val keyCount = (pitchHigh - pitchLow + 1).toFloat()
        val keyWidth = w / keyCount
        val hitLineY = h * 0.82f
        val keyboardHeight = (h - hitLineY).coerceAtLeast(0f)
        val rollHeight = hitLineY
        val windowTicks = (lookaheadBeats * score.divisions).coerceAtLeast(1f)
        val ppt = rollHeight / windowTicks // pixels per tick

        drawBackground(h)
        drawBeatGrid(score, tick, ppt, hitLineY, w)
        drawNotes(score.notes, tick, ppt, hitLineY, pitchLow, keyWidth, h, hostedTrack)
        drawHitLine(hitLineY, w)
        drawHeldFeedback(held, pitchLow, keyWidth, hitLineY, keyboardHeight)
        drawKeyboard(pitchLow, pitchHigh, keyWidth, hitLineY, keyboardHeight, w, held)
    }
}

private fun DrawScope.drawBackground(h: Float) {
    drawRect(BG_TOP, Offset.Zero, Size(size.width, h))
    // 顶部到底部的轻微渐变（用半透明叠加近似）
    drawRect(
        color = BG_BOTTOM.copy(alpha = 0.6f),
        topLeft = Offset(0f, h * 0.4f),
        size = Size(size.width, h * 0.6f),
    )
}

private fun DrawScope.drawBeatGrid(
    score: Score,
    tick: Long,
    ppt: Float,
    hitLineY: Float,
    w: Float,
) {
    val beatsPerWindow = (hitLineY / ppt / score.divisions).toInt() + 2
    val currentBeat = (tick / score.divisions).toInt()
    for (i in -1..beatsPerWindow) {
        val beat = currentBeat + i
        val beatTick = beat.toLong() * score.divisions
        val y = hitLineY + (tick - beatTick) * ppt
        if (y in 0f..hitLineY) {
            drawLine(
                color = GRID,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = if (beat % 4 == 0) 1.5f else 0.8f,
            )
        }
    }
}

private fun DrawScope.drawHitLine(hitLineY: Float, w: Float) {
    drawLine(
        color = HIT_LINE.copy(alpha = 0.8f),
        start = Offset(0f, hitLineY),
        end = Offset(w, hitLineY),
        strokeWidth = 2f,
    )
}

private fun DrawScope.drawNotes(
    notes: List<NoteEntity>,
    tick: Long,
    ppt: Float,
    hitLineY: Float,
    pitchLow: Int,
    keyWidth: Float,
    canvasH: Float,
    hostedTrack: TrackType?,
) {
    val h = canvasH
    val gap = (keyWidth * 0.12f).coerceAtLeast(1f)
    val noteW = keyWidth - gap * 2
    val radius = androidx.compose.ui.geometry.CornerRadius(keyWidth * 0.18f, keyWidth * 0.18f)
    for (note in notes) {
        // 视口裁剪：只画与屏幕相交的音符
        val bottomY = hitLineY + (tick - note.startTick) * ppt
        val topY = bottomY - note.durationTick * ppt
        if (topY >= h || bottomY <= 0f) continue

        val x = (note.pitch - pitchLow) * keyWidth + gap
        val clampedTop = topY.coerceAtLeast(0f)
        val clampedBottom = bottomY.coerceAtMost(h)
        val isCurrent = note.startTick == tick && !note.isHit
        // 托管轨道视觉弱化至 0.3 透明度（文档 4.2）
        val dim = note.track == hostedTrack
        var color = colorFor(note, isCurrent)
        if (dim) color = color.copy(alpha = color.alpha * 0.3f)

        // 当前待弹组：先描一圈高亮，提示“现在该弹”
        if (isCurrent && !dim) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(x - gap, clampedTop),
                size = Size(noteW + gap * 2, (clampedBottom - clampedTop).coerceAtLeast(2f)),
                cornerRadius = radius,
            )
        }
        drawRoundRect(
            color = color,
            topLeft = Offset(x, clampedTop),
            size = Size(noteW, (clampedBottom - clampedTop).coerceAtLeast(2f)),
            cornerRadius = radius,
        )
    }
}

private fun DrawScope.drawKeyboard(
    pitchLow: Int,
    pitchHigh: Int,
    keyWidth: Float,
    hitLineY: Float,
    keyboardHeight: Float,
    w: Float,
    held: Set<Int>,
) {
    if (keyboardHeight <= 0f) return
    val top = hitLineY
    drawRect(Color(0xFF15171B), Offset(0f, top), Size(w, keyboardHeight))
    for (pitch in pitchLow..pitchHigh) {
        val x = (pitch - pitchLow) * keyWidth
        val isBlack = pitch % 12 in BLACK_KEY_OFFSETS
        val isHeld = pitch in held
        if (isBlack) {
            drawRect(
                color = if (isHeld) Color(0xFF6EC0FF) else BLACK_KEY,
                topLeft = Offset(x + keyWidth * 0.2f, top),
                size = Size(keyWidth * 0.6f, keyboardHeight),
            )
        } else {
            drawRect(
                color = if (isHeld) Color(0xFFBFE8FF) else WHITE_KEY,
                topLeft = Offset(x + keyWidth * 0.05f, top),
                size = Size(keyWidth * 0.9f, keyboardHeight),
            )
        }
    }
    drawLine(color = Color.Black, start = Offset(0f, top), end = Offset(w, top), strokeWidth = 1f)
}

/**
 * 用户按键反馈（防盲弹）：在每个当前按下的音高处，于判定线上方绘制高亮发光块，
 * 让长辈直观看到“我按了这个键”。
 */
private fun DrawScope.drawHeldFeedback(
    held: Set<Int>,
    pitchLow: Int,
    keyWidth: Float,
    hitLineY: Float,
    keyboardHeight: Float,
) {
    if (held.isEmpty()) return
    val blockH = (keyboardHeight * 1.6f).coerceAtLeast(28f)
    for (pitch in held) {
        val rel = pitch - pitchLow
        if (rel < 0) continue
        val x = rel * keyWidth + keyWidth * 0.1f
        val w = keyWidth * 0.8f
        // 从判定线向上的发光块
        drawRoundRect(
            color = Color(0xFF7FD0FF).copy(alpha = 0.65f),
            topLeft = Offset(x, hitLineY - blockH),
            size = Size(w, blockH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(keyWidth * 0.18f, keyWidth * 0.18f),
        )
    }
}

private fun colorFor(note: NoteEntity, isCurrent: Boolean): Color = when {
    note.isHit -> when (note.track) {
        TrackType.RIGHT_HAND -> RIGHT_HIT
        TrackType.LEFT_HAND -> LEFT_HIT
    }
    isCurrent -> when (note.track) {
        // 当前待弹组用高饱和度
        TrackType.RIGHT_HAND -> RIGHT_HIT.copy(alpha = 0.95f)
        TrackType.LEFT_HAND -> LEFT_HIT.copy(alpha = 0.95f)
    }
    else -> when (note.track) {
        // 未来音符用低饱和度
        TrackType.RIGHT_HAND -> RIGHT_UNHIT
        TrackType.LEFT_HAND -> LEFT_UNHIT
    }
}
