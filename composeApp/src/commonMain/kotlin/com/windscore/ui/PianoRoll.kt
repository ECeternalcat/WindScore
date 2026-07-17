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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.windscore.core.model.NoteEntity
import com.windscore.core.model.Score
import com.windscore.core.model.TrackType

import com.windscore.ui.theme.WindScoreColors

// 左手：薄荷绿（冷 pastel）
private val LEFT_HIT get() = WindScoreColors.blockMint
private val LEFT_UNHIT get() = if (com.windscore.ui.theme.isDarkThemeGlobal) Color(0xFF5D7D62) else Color(0xFF9DC9A4)
// 右手：珊瑚橙（暖 pastel）
private val RIGHT_HIT get() = WindScoreColors.blockCoral
private val RIGHT_UNHIT get() = if (com.windscore.ui.theme.isDarkThemeGlobal) Color(0xFF8C6654) else Color(0xFFD49A7E)

private val BG_TOP get() = WindScoreColors.canvas
private val BG_BOTTOM get() = WindScoreColors.surfaceSoft
private val HIT_LINE get() = WindScoreColors.ink
private val WHITE_KEY get() = WindScoreColors.canvas
private val BLACK_KEY get() = WindScoreColors.inverseCanvas
private val GRID get() = WindScoreColors.hairline.copy(alpha = 0.5f)

private val BLACK_KEY_OFFSETS = setOf(1, 3, 6, 8, 10)

private val clockStart = kotlin.time.TimeSource.Monotonic.markNow()

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
    pulseIntensityProvider: () -> Float = { 0.15f },
    bpmProvider: () -> Int = { 120 },
    pendingLoopStart: Long? = null,
    loopRange: LongRange? = null,
) {
    if (score.notes.isEmpty()) {
        Box(modifier.fillMaxSize())
        return
    }

    val pitchMin = score.notes.minOf { it.pitch }
    val pitchMax = score.notes.maxOf { it.pitch }
    val pitchLow = (pitchMin - 2).coerceIn(21, 108)
    val pitchHigh = (pitchMax + 2).coerceIn(21, 108)
    val textMeasurer = rememberTextMeasurer()

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

        drawBackground(h, pulseIntensityProvider(), bpmProvider())
        drawBeatGrid(score, tick, ppt, hitLineY, w)
        drawNotes(score.notes, tick, ppt, hitLineY, pitchLow, keyWidth, h, hostedTrack, textMeasurer)
        drawHitLine(hitLineY, w)
        drawLoopCursors(tick, ppt, hitLineY, w, h, pendingLoopStart, loopRange)
        drawHeldFeedback(held, pitchLow, keyWidth, hitLineY, keyboardHeight)
        drawKeyboard(pitchLow, pitchHigh, keyWidth, hitLineY, keyboardHeight, w, held)
    }
}

private fun DrawScope.drawBackground(h: Float, pulseIntensity: Float, bpm: Int) {
    drawRect(BG_TOP, Offset.Zero, Size(size.width, h))
    // 顶部到底部的轻微渐变（用 surface-soft 叠加近似）
    drawRect(
        color = BG_BOTTOM.copy(alpha = 0.5f),
        topLeft = Offset(0f, h * 0.4f),
        size = Size(size.width, h * 0.6f),
    )
    // Phase 3：动态节拍脉冲——按 BPM 闪烁的水平光带，卡住时变暗
    if (bpm > 0) {
        val beatMs = 60_000.0 / bpm
        val currentMs = clockStart.elapsedNow().inWholeMilliseconds
        val phase = (currentMs % beatMs.toLong()) / beatMs
        val sine = kotlin.math.sin(phase * kotlin.math.PI).coerceIn(0.0, 1.0)
        val bandAlpha = (sine * pulseIntensity * 0.18f).toFloat()
        if (bandAlpha > 0.01f) {
            val bandH = h * 0.08f
            drawRect(
                color = WindScoreColors.blockLilac.copy(alpha = bandAlpha),
                topLeft = Offset(0f, h * 0.35f),
                size = Size(size.width, bandH),
            )
        }
    }
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
        color = HIT_LINE.copy(alpha = 0.85f),
        start = Offset(0f, hitLineY),
        end = Offset(w, hitLineY),
        strokeWidth = 2f,
    )
}

private fun DrawScope.drawLoopCursors(
    tick: Long,
    ppt: Float,
    hitLineY: Float,
    w: Float,
    h: Float,
    pendingLoopStart: Long?,
    loopRange: LongRange?
) {
    val loopColor = Color(0xFFFFB300).copy(alpha = 0.85f)
    val loopBgColor = Color(0xFFFFB300).copy(alpha = 0.15f)

    fun drawLineAtTick(targetTick: Long) {
        val y = hitLineY + (tick - targetTick) * ppt
        if (y in 0f..h) {
            drawLine(
                color = loopColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 4f,
            )
        }
    }

    if (loopRange != null) {
        val yStart = hitLineY + (tick - loopRange.first) * ppt
        val yEnd = hitLineY + (tick - loopRange.last) * ppt
        
        val topY = minOf(yStart, yEnd).coerceAtLeast(0f)
        val bottomY = maxOf(yStart, yEnd).coerceAtMost(h)
        
        if (bottomY > 0 && topY < h) {
            drawRect(
                color = loopBgColor,
                topLeft = Offset(0f, topY),
                size = Size(w, bottomY - topY)
            )
        }
        
        drawLineAtTick(loopRange.first)
        drawLineAtTick(loopRange.last)
    } else if (pendingLoopStart != null) {
        drawLineAtTick(pendingLoopStart)
    }
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
    textMeasurer: TextMeasurer,
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

        // Phase 3：绘制指法数字，极大字号、高对比度，帮助长辈安排手指
        note.fingering?.let { fingering ->
            val noteHeight = clampedBottom - clampedTop
            if (noteHeight > keyWidth * 0.35f) {
                val fontSize = (keyWidth * 0.55f).coerceIn(10f, 36f).sp
                val textStyle = TextStyle(
                    color = Color.White,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                )
                val layout = textMeasurer.measure(fingering, textStyle)
                val textX = x + (noteW - layout.size.width) / 2f
                val textY = clampedTop + (noteHeight - layout.size.height) / 2f
                drawText(layout, topLeft = Offset(textX, textY))
            }
        }
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
    drawRect(WindScoreColors.surfaceSoft, Offset(0f, top), Size(w, keyboardHeight))
    for (pitch in pitchLow..pitchHigh) {
        val x = (pitch - pitchLow) * keyWidth
        val isBlack = pitch % 12 in BLACK_KEY_OFFSETS
        val isHeld = pitch in held
        if (isBlack) {
            drawRect(
                color = if (isHeld) WindScoreColors.blockLilac else BLACK_KEY,
                topLeft = Offset(x + keyWidth * 0.2f, top),
                size = Size(keyWidth * 0.6f, keyboardHeight),
            )
        } else {
            drawRect(
                color = if (isHeld) WindScoreColors.blockLilac.copy(alpha = 0.5f) else WHITE_KEY,
                topLeft = Offset(x + keyWidth * 0.05f, top),
                size = Size(keyWidth * 0.9f, keyboardHeight),
            )
        }
        // 白键之间细线
        if (!isBlack) {
            drawLine(
                color = WindScoreColors.hairline,
                start = Offset(x, top),
                end = Offset(x, top + keyboardHeight),
                strokeWidth = 0.8f,
            )
        }
    }
    // C 键定位标记：每个八度的 C 用淡点，中央 C（MIDI 60）用品红实心点突出
    for (pitch in pitchLow..pitchHigh) {
        if (pitch % 12 != 0) continue
        val cx = (pitch - pitchLow) * keyWidth + keyWidth / 2f
        val cy = top + keyboardHeight * 0.86f
        if (pitch == 60) {
            drawCircle(
                color = WindScoreColors.accentMagenta,
                radius = (keyWidth * 0.17f).coerceAtLeast(3.5f),
                center = Offset(cx, cy),
            )
        } else {
            drawCircle(
                color = WindScoreColors.ink.copy(alpha = 0.28f),
                radius = (keyWidth * 0.1f).coerceAtLeast(2f),
                center = Offset(cx, cy),
            )
        }
    }
    drawLine(color = WindScoreColors.hairline, start = Offset(0f, top), end = Offset(w, top), strokeWidth = 1f)
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
            color = WindScoreColors.accentMagenta.copy(alpha = 0.35f),
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
