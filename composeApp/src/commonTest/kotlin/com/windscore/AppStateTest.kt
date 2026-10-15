package com.windscore

import com.windscore.core.model.PracticeMode
import com.windscore.core.model.Score
import com.windscore.core.model.ScoreSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppStateTest {
    @Test
    fun legacyVariableDivisionsBookmarksAreNotRestoredAsCorrectedCoordinates() {
        val original = ScoreSettings(currentTick = 4, loopStartTick = 4, loopEndTick = 12, speedMultiplier = 0.5)
        val restored = original.restorePositionFor(score("a", "Score", 100).copy(legacyTimingCompatible = false))
        assertEquals(0L, restored.currentTick)
        assertNull(restored.loopStartTick)
        assertNull(restored.loopEndTick)
        assertEquals(0.5, restored.speedMultiplier)
        assertEquals(4L, original.currentTick)
        assertEquals(original, original.restorePositionFor(score("a", "Score", 100)))
    }

    @Test
    fun versionedBookmarkCoordinatesCanBeRescaledWithoutChangingPreferences() {
        val settings = ScoreSettings(scoreVersion = 2, divisions = 4, currentTick = 4, loopStartTick = 4, loopEndTick = 8)
        val restored = settings.restorePositionFor(score("a", "Score", 100).copy(divisions = 8))
        assertEquals(8L, restored.currentTick)
        assertEquals(8L, restored.loopStartTick)
        assertEquals(16L, restored.loopEndTick)
    }

    @Test
    fun scoreIdentityUsesStableIdBeforeMetadata() {
        val first = score(id = "a", title = "Same", ticks = 8)
        val second = score(id = "b", title = "Same", ticks = 8)

        assertFalse(sameScoreIdentity(first, second))
        assertTrue(sameScoreIdentity(first, first.copy(title = "Renamed")))
        assertTrue(sameScoreIdentity(first.copy(id = ""), second.copy(id = "")))
    }

    @Test
    fun settingsPersistOnlyActiveLoopAndResetFinishedTick() {
        val score = score(id = "a", title = "Score", ticks = 100)
        val settings = createScoreSettings(
            score = score,
            practiceMode = PracticeMode.RIGHT_ONLY,
            viewMode = ViewMode.ROLL,
            playbackSpeed = 0.75f,
            metronomeVol = 40,
            countdownEnabled = true,
            pendingLoopStart = 12,
            currentTick = 100,
            activeLoopRange = null,
        )

        assertEquals(12, settings.loopStartTick)
        assertNull(settings.loopEndTick)
        assertEquals(0, settings.currentTick)
    }

    @Test
    fun incompleteLoopRestoresEndSelectionPhase() {
        assertEquals(
            LoopSetupState.WAITING_FOR_END,
            restoreLoopSetupState(ScoreSettings(loopStartTick = 12, loopEndTick = null)),
        )
        assertEquals(
            LoopSetupState.IDLE,
            restoreLoopSetupState(ScoreSettings(loopStartTick = 12, loopEndTick = 36)),
        )
    }

    private fun score(id: String, title: String, ticks: Long) = Score(
        id = id,
        title = title,
        notes = emptyList(),
        divisions = 4,
        tempo = 120,
        totalTicks = ticks,
    )
}
