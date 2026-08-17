package com.windscore.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.windscore.ViewMode
import com.windscore.audio.AudioSynth
import com.windscore.core.engine.ScoreSession
import com.windscore.core.model.NoteEntity
import com.windscore.core.model.PracticeMode
import com.windscore.core.model.Score
import com.windscore.core.model.TrackType
import com.windscore.ui.theme.WindScoreTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class ControlSidebarUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val session = ScoreSession(score(), NoOpSynth, scope)

    @After
    fun tearDown() {
        session.close()
        scope.cancel()
    }

    @Test
    fun reopenedScoreShowsRestoredSheetViewAndLoopControls() {
        composeRule.setContent {
            WindScoreTheme {
                ControlSidebarHarness(
                    session = session,
                    initialViewMode = ViewMode.SHEET_FULL,
                    initialLoopRange = 12L..36L,
                )
            }
        }

        composeRule.onNodeWithTag("view-roll").assertIsNotSelected()
        composeRule.onNodeWithTag("view-sheet-keyboard").assertIsNotSelected()
        composeRule.onNodeWithTag("view-sheet-full").assertIsSelected()
        composeRule.onNodeWithTag("loop-start").assertIsSelected()
        composeRule.onNodeWithTag("loop-end").assertIsSelected()
        composeRule.onNodeWithTag("loop-clear").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("loop-start").assertIsNotSelected()
        composeRule.onNodeWithTag("loop-end").assertIsNotSelected().assertIsNotEnabled()
        composeRule.onNodeWithTag("loop-clear").assertIsNotEnabled()
    }

    @Test
    fun reopenedPendingLoopAllowsSelectingEnd() {
        var endRequested = false
        composeRule.setContent {
            WindScoreTheme {
                ControlSidebarHarness(
                    session = session,
                    initialViewMode = ViewMode.ROLL,
                    initialPendingLoopStart = 12L,
                    onLoopEnd = { endRequested = true },
                )
            }
        }

        composeRule.onNodeWithTag("loop-start").assertIsSelected()
        composeRule.onNodeWithTag("loop-end").assertIsEnabled().assertIsNotSelected().performClick()
        composeRule.runOnIdle { assertTrue(endRequested) }
    }

    private companion object {
        @androidx.compose.runtime.Composable
        fun ControlSidebarHarness(
            session: ScoreSession,
            initialViewMode: ViewMode,
            initialPendingLoopStart: Long? = null,
            initialLoopRange: LongRange? = null,
            onLoopEnd: () -> Unit = {},
        ) {
            var viewMode by remember { mutableStateOf(initialViewMode) }
            var pendingLoopStart by remember { mutableStateOf(initialPendingLoopStart) }
            var loopRange by remember { mutableStateOf(initialLoopRange) }

            ControlSidebar(
                session = session,
                practiceMode = PracticeMode.BOTH,
                viewMode = viewMode,
                pendingLoopStart = pendingLoopStart,
                scoreHasSheet = true,
                isPlaying = false,
                sheetReady = true,
                running = false,
                playbackSpeed = 1f,
                onPlaybackSpeedChange = {},
                onToggleMidi = {},
                countdownEnabled = false,
                onCountdownEnabledChange = {},
                metronomeVol = 50,
                onMetronomeVolChange = {},
                onToggleMute = {},
                onModeChange = {},
                onViewChange = { viewMode = it },
                onLoopStart = {},
                onLoopEnd = onLoopEnd,
                onClearLoop = {
                    pendingLoopStart = null
                    loopRange = null
                },
                onTogglePlay = {},
                onStopPlay = {},
                activeLoopRange = loopRange,
            )
        }

        fun score() = Score(
            id = "ui-test-score",
            title = "UI test score",
            notes = listOf(NoteEntity(0, 60, TrackType.RIGHT_HAND, 0, 4)),
            divisions = 4,
            tempo = 120,
            totalTicks = 64,
            supportsSheetView = true,
        )

        object NoOpSynth : AudioSynth {
            override fun start() = Unit
            override fun noteOn(channel: Int, pitch: Int, velocity: Int) = Unit
            override fun noteOff(channel: Int, pitch: Int) = Unit
            override fun stop() = Unit
        }
    }
}
