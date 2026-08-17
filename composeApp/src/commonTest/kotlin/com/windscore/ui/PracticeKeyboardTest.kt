package com.windscore.ui

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PracticeKeyboardTest {
    @Test
    fun scoreRangeExpandsToStableOctaveBoundaries() {
        assertEquals(60..83, practiceKeyboardRange(64, 79))
        assertEquals(21..35, practiceKeyboardRange(21, 24))
        assertEquals(96..108, practiceKeyboardRange(107, 108))
    }

    @Test
    fun blackKeysOverlayWhiteKeyBoundaries() {
        val layout = practiceKeyLayout(60..71)
        val c = layout.single { it.pitch == 60 }
        val cSharp = layout.single { it.pitch == 61 }
        val d = layout.single { it.pitch == 62 }

        assertFalse(c.isBlack)
        assertTrue(cSharp.isBlack)
        assertFalse(d.isBlack)
        assertTrue(cSharp.widthFraction < c.widthFraction)
        assertTrue(abs((cSharp.leftFraction + cSharp.widthFraction / 2f) - d.leftFraction) < 0.0001f)
    }

    @Test
    fun threeNoteChordKeepsEveryTargetInTargetState() {
        val targets = setOf(73, 77, 80)
        assertEquals(
            listOf(PracticeKeyState.TARGET, PracticeKeyState.TARGET, PracticeKeyState.TARGET),
            targets.sorted().map { practiceKeyState(it in targets, isHosted = false, isHeld = false) },
        )
    }

    @Test
    fun heldTargetRetainsHeldPriorityAndHostedUsesSeparateState() {
        assertEquals(PracticeKeyState.HELD, practiceKeyState(isTarget = true, isHosted = false, isHeld = true))
        assertEquals(PracticeKeyState.HOSTED, practiceKeyState(isTarget = false, isHosted = true, isHeld = false))
        assertEquals(PracticeKeyState.TARGET, practiceKeyState(isTarget = true, isHosted = true, isHeld = false))
    }
}
