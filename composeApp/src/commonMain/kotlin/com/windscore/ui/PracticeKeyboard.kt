package com.windscore.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

private val WHITE_PITCH_CLASSES = setOf(0, 2, 4, 5, 7, 9, 11)

internal enum class PracticeKeyState { IDLE, HOSTED, TARGET, HELD }

internal data class PracticeKeyLayout(
    val pitch: Int,
    val isBlack: Boolean,
    val leftFraction: Float,
    val widthFraction: Float,
)

internal fun practiceKeyState(isTarget: Boolean, isHosted: Boolean, isHeld: Boolean): PracticeKeyState = when {
    isHeld -> PracticeKeyState.HELD
    isTarget -> PracticeKeyState.TARGET
    isHosted -> PracticeKeyState.HOSTED
    else -> PracticeKeyState.IDLE
}

internal fun practiceKeyboardRange(pitchLow: Int, pitchHigh: Int): IntRange {
    val low = pitchLow.coerceIn(21, 108)
    val high = pitchHigh.coerceIn(low, 108)
    val octaveLow = (low - low.mod(12)).coerceAtLeast(21)
    val octaveHigh = (high + (11 - high.mod(12))).coerceAtMost(108)
    return octaveLow..octaveHigh.coerceAtLeast(octaveLow)
}

internal fun practiceKeyLayout(range: IntRange): List<PracticeKeyLayout> {
    val whitePitches = range.filter { it.mod(12) in WHITE_PITCH_CLASSES }
    if (whitePitches.isEmpty()) return emptyList()
    val whiteWidth = 1f / whitePitches.size
    val whiteIndex = whitePitches.withIndex().associate { it.value to it.index }
    return range.mapNotNull { pitch ->
        val index = whiteIndex[pitch]
        if (index != null) {
            PracticeKeyLayout(pitch, isBlack = false, index * whiteWidth, whiteWidth)
        } else {
            val precedingWhite = whitePitches.indexOfLast { it < pitch }
            if (precedingWhite < 0) return@mapNotNull null
            val width = whiteWidth * 0.62f
            val boundary = (precedingWhite + 1) * whiteWidth
            PracticeKeyLayout(pitch, isBlack = true, boundary - width / 2f, width)
        }
    }
}

@Composable
fun PracticeKeyboard(
    modifier: Modifier = Modifier,
    pitchLow: Int = 21,
    pitchHigh: Int = 108,
    targetPitches: Set<Int> = emptySet(),
    hostedPitches: Set<Int> = emptySet(),
    heldPitches: Set<Int> = emptySet(),
) {
    val range = practiceKeyboardRange(pitchLow, pitchHigh)
    val layout = practiceKeyLayout(range)
    Canvas(modifier.fillMaxSize()) {
        drawPracticeKeyboard(layout, targetPitches, hostedPitches, heldPitches)
    }
}

private fun DrawScope.drawPracticeKeyboard(
    layout: List<PracticeKeyLayout>,
    targets: Set<Int>,
    hosted: Set<Int>,
    held: Set<Int>,
) {
    if (layout.isEmpty()) return
    val boardPadding = size.height * 0.055f
    val keyTop = boardPadding
    val whiteHeight = size.height - boardPadding * 1.7f
    val gap = (size.width * 0.0015f).coerceAtLeast(1f)

    drawRoundRect(
        color = KeyboardColors.board,
        cornerRadius = CornerRadius(size.height * 0.07f),
    )

    layout.filterNot { it.isBlack }.forEach { key ->
        val left = key.leftFraction * size.width + gap / 2f
        val width = key.widthFraction * size.width - gap
        val state = practiceKeyState(key.pitch in targets, key.pitch in hosted, key.pitch in held)
        drawRoundRect(
            color = when (state) {
                PracticeKeyState.HELD -> KeyboardColors.heldWhite
                PracticeKeyState.TARGET -> KeyboardColors.targetWhite
                PracticeKeyState.HOSTED -> KeyboardColors.hostedWhite
                PracticeKeyState.IDLE -> KeyboardColors.whiteKey
            },
            topLeft = Offset(left, keyTop),
            size = Size(width, whiteHeight),
            cornerRadius = CornerRadius(size.height * 0.025f),
        )
        if (key.pitch == 60) {
            drawCircle(
                color = KeyboardColors.middleC,
                radius = (width * 0.09f).coerceAtLeast(2.5f),
                center = Offset(left + width / 2f, keyTop + whiteHeight * 0.91f),
            )
        }
    }

    layout.filter { it.isBlack }.forEach { key ->
        val left = key.leftFraction * size.width
        val width = key.widthFraction * size.width
        val blackHeight = whiteHeight * 0.62f
        val state = practiceKeyState(key.pitch in targets, key.pitch in hosted, key.pitch in held)
        drawRoundRect(
            color = when (state) {
                PracticeKeyState.HELD -> KeyboardColors.heldBlack
                PracticeKeyState.TARGET -> KeyboardColors.targetBlack
                PracticeKeyState.HOSTED -> KeyboardColors.hostedBlack
                PracticeKeyState.IDLE -> KeyboardColors.blackKey
            },
            topLeft = Offset(left, keyTop),
            size = Size(width, blackHeight),
            cornerRadius = CornerRadius(size.height * 0.022f),
        )
    }
}

private object KeyboardColors {
    val board = Color(0xFFE3E5E2)
    val whiteKey = Color(0xFFFAFAF8)
    val blackKey = Color(0xFF202124)
    val targetWhite = Color(0xFFFFD7E7)
    val targetBlack = Color(0xFFD92F73)
    val hostedWhite = Color(0xFFD3D6D2)
    val hostedBlack = Color(0xFF737773)
    val heldWhite = Color(0xFFBEE4A7)
    val heldBlack = Color(0xFF79B85D)
    val middleC = Color(0xFF5A5D59)
}
