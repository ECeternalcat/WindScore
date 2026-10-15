package com.windscore.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.windscore.ViewMode
import com.windscore.audio.AudioSynth
import com.windscore.core.engine.ScoreSession
import com.windscore.core.model.*
import com.windscore.ui.i18n.AppLanguage
import com.windscore.ui.i18n.AppStrings
import com.windscore.ui.theme.WindScoreTheme
import kotlinx.coroutines.*
import org.junit.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LearningViewsUiTest {
    @get:Rule val compose=createComposeRule()
    private val language=AppStrings.language
    private val scope=CoroutineScope(Dispatchers.Default+SupervisorJob())
    private val score=Score(id="learning",version=4,divisions=4,tempo=120,totalTicks=16,
        supportsSheetView=true,notes=listOf(
            NoteEntity(0,60,TrackType.RIGHT_HAND,0,4),NoteEntity(1,64,TrackType.RIGHT_HAND,0,4),
            NoteEntity(2,67,TrackType.RIGHT_HAND,8,4)))
    private val session=ScoreSession(score,SilentSynth,scope)
    @Before fun english() { AppStrings.update(AppLanguage.ENGLISH) }
    @After fun close() { session.close();scope.cancel();AppStrings.update(language) }

    @Test fun switchingViewsDuringAPartialChordAndLoopDoesNotChangeJudgment() {
        session.setLoop(0,16)
        val view=mutableStateOf(ViewMode.ROLL)
        compose.setContent { WindScoreTheme { CompactControls(view) } }
        compose.runOnIdle { session.onNoteOn(60);session.onNoteOn(61) }
        val before=session.state.value
        assertEquals(setOf(60,64),before.pendingPitches)
        assertEquals(setOf(60,61),before.heldPitches)
        assertTrue(before.hitNoteIds.isEmpty())
        for((tag,label) in listOf("view-sheet-keyboard" to "Score + keyboard","view-sheet-full" to "Score only","view-roll" to "Falling notes")) {
            compose.onNodeWithContentDescription(label).assertExists()
            compose.onNodeWithTag(tag).performClick().assertIsSelected()
            compose.runOnIdle {
                assertEquals(before.currentTick,session.currentTick)
                assertEquals(before.heldPitches,session.state.value.heldPitches)
                assertEquals(before.pendingPitches,session.pendingPitches)
                assertEquals(before.hitNoteIds,session.state.value.hitNoteIds)
                assertEquals(1,session.wrongPressCount())
                assertEquals(0L..16L,session.loopRange)
            }
        }
        compose.runOnIdle { session.onNoteOff(61);session.onNoteOn(64) }
        assertEquals(8L,session.currentTick)
        compose.onNodeWithTag("view-sheet-full").performClick()
        compose.onNodeWithTag("view-sheet-keyboard").performClick()
        compose.runOnIdle { session.onNoteOff(60);session.onNoteOff(64);session.onNoteOn(67) }
        assertEquals(0L,session.currentTick)
        assertEquals(setOf(60,64),session.pendingPitches)
        assertTrue(session.isLoopActive)
        assertFalse(session.finished)
    }

    @Test fun viewTooltipDescribesTheChoiceWithoutChangingPractice() {
        val view=mutableStateOf(ViewMode.ROLL)
        compose.setContent { WindScoreTheme { CompactControls(view) } }
        compose.onNodeWithTag("view-sheet-keyboard").performTouchInput { longClick() }
        compose.onNodeWithText("Score + keyboard").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(ViewMode.ROLL,view.value)
            assertEquals(0L,session.currentTick)
        }
    }

    @Test fun independentReadingHidesPitchAnswersButKeepsProgressAndResults() {
        val view=mutableStateOf(ViewMode.SHEET_KEYBOARD)
        val compact=mutableStateOf(false)
        compose.setContent { WindScoreTheme { Column {
            if(compact.value) CompactControls(view) else StatusCard(session,view.value)
        } } }
        for(isCompact in listOf(false,true)) {
            compose.runOnIdle { compact.value=isCompact;view.value=ViewMode.SHEET_KEYBOARD }
            compose.onAllNodesWithText("C4",substring=true).assertCountEquals(1)
            compose.runOnIdle { view.value=ViewMode.SHEET_FULL }
            compose.onAllNodesWithText("C4",substring=true).assertCountEquals(0)
            compose.onAllNodesWithText("E4",substring=true).assertCountEquals(0)
            compose.onNodeWithText("0/2",substring=true).assertExists()
            compose.runOnIdle { view.value=ViewMode.ROLL }
            compose.onAllNodesWithText("C4",substring=true).assertCountEquals(1)
        }
        compose.runOnIdle { session.onNoteOn(60);session.onNoteOn(64);session.onNoteOn(67);view.value=ViewMode.SHEET_FULL }
        compose.onNodeWithText("Finished · Accuracy 100%").assertExists()
        compose.runOnIdle { compact.value=false }
        compose.onNodeWithText("Performance complete").assertExists()
    }

    @Composable private fun CompactControls(view: MutableState<ViewMode>) {
        PlayerControlBar(session,PracticeMode.BOTH,view.value,null,true,true,false,true,
            {},{view.value=it},{},{},{},{},{},activeLoopRange=session.takeIf { it.isLoopActive }?.loopRange)
    }

    private object SilentSynth: AudioSynth {
        override fun start()=Unit
        override fun stop()=Unit
        override fun noteOn(channel:Int,pitch:Int,velocity:Int)=Unit
        override fun noteOff(channel:Int,pitch:Int)=Unit
    }
}
