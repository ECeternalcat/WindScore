package com.windscore.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class WindScoreThemeTest {
    @Test
    fun lightAndDarkPalettesKeepSemanticRolesDistinct() {
        val light = windScorePalette(darkTheme = false)
        val dark = windScorePalette(darkTheme = true)

        assertNotEquals(light.canvas, dark.canvas)
        assertNotEquals(light.ink, dark.ink)
        assertNotEquals(light.leftNoteUnhit, dark.leftNoteUnhit)
        assertEquals(light.accentMagenta, dark.accentMagenta)
    }
}
