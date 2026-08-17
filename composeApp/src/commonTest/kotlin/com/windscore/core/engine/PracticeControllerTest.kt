package com.windscore.core.engine

import com.windscore.audio.AudioSynth
import com.windscore.core.model.NoteEntity
import com.windscore.core.model.PracticeMode
import com.windscore.core.model.Score
import com.windscore.core.model.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PracticeControllerTest {
    @Test
    fun changingModePreservesTickAndLoopInANewSession() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val controller = PracticeController(RecordingSynth(), scope)
            val original = controller.load(score(), PracticeMode.BOTH, currentTick = 8, loopRange = 4L..12L)

            val replacement = controller.changeMode(PracticeMode.RIGHT_ONLY)

            assertNotSame(original, replacement)
            assertEquals(PracticeMode.RIGHT_ONLY, controller.state.value.mode)
            assertEquals(8L, replacement?.currentTick)
            assertEquals(4L..12L, replacement?.loopRange)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun inputIsRoutedToCurrentSession() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val controller = PracticeController(RecordingSynth(), scope)
            val session = controller.load(score(), PracticeMode.BOTH)

            controller.onNoteOn(60)

            assertEquals(4L, session.currentTick)
            assertTrue(60 in session.heldPitches)
            controller.onNoteOff(60)
            assertTrue(session.heldPitches.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun closeClearsOwnedSession() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val controller = PracticeController(RecordingSynth(), scope)
            controller.load(score(), PracticeMode.BOTH)

            controller.close()

            assertNull(controller.state.value.session)
            assertNull(controller.state.value.score)
            assertEquals(PracticeMode.BOTH, controller.state.value.mode)
        } finally {
            scope.cancel()
        }
    }

    private fun score() = Score(
        notes = listOf(
            NoteEntity(0, 60, TrackType.RIGHT_HAND, 0, 2),
            NoteEntity(1, 62, TrackType.RIGHT_HAND, 4, 2),
            NoteEntity(2, 64, TrackType.RIGHT_HAND, 8, 2),
            NoteEntity(3, 65, TrackType.RIGHT_HAND, 12, 2),
        ),
        divisions = 4,
        tempo = 120,
        totalTicks = 16,
    )

    private class RecordingSynth : AudioSynth {
        override fun start() = Unit
        override fun noteOn(channel: Int, pitch: Int, velocity: Int) = Unit
        override fun noteOff(channel: Int, pitch: Int) = Unit
        override fun stop() = Unit
    }
}
