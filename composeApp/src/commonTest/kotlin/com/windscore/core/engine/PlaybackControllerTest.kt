package com.windscore.core.engine

import com.windscore.audio.AudioSynth
import com.windscore.core.model.NoteEntity
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackControllerTest {
    @Test
    fun loadRestoresPlaybackParametersAndTimeline() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val score = score()
            val session = ScoreSession(score, RecordingSynth(), scope)
            session.seekTo(4)
            val controller = PlaybackController(RecordingSynth(), scope)

            controller.load(
                score = score,
                session = session,
                initialTick = 4,
                loopRange = 4L..12L,
                speed = 0.75f,
                metronomeVolume = 35,
            )

            assertEquals(4L, controller.state.value.tick)
            assertEquals(4L..12L, controller.state.value.loopRange)
            assertEquals(0.75f, controller.state.value.speed)
            assertEquals(35, controller.state.value.metronomeVolume)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun seekStepAndLoopKeepSessionAndPlayerAligned() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val score = score()
            val session = ScoreSession(score, RecordingSynth(), scope)
            val controller = PlaybackController(RecordingSynth(), scope)
            controller.load(score, session)

            controller.seekTo(8)
            controller.setLoop(4, 12)
            eventually { controller.state.value.tick == 4L }
            assertEquals(4L, controller.state.value.tick)
            assertEquals(4L, session.currentTick)

            controller.stepForward()
            eventually { controller.state.value.tick == 8L }
            assertEquals(8L, controller.state.value.tick)
            assertEquals(8L, session.currentTick)

            controller.stepBackward()
            eventually { controller.state.value.tick == 4L }
            assertEquals(4L, controller.state.value.tick)
            assertEquals(4L, session.currentTick)

            controller.clearLoop()
            eventually { controller.state.value.tick == 0L }
            assertEquals(0L, controller.state.value.tick)
            assertEquals(0L, session.currentTick)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun playbackDisablesHostedSchedulingAndRestoresItWhenStopped() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val score = score()
            val session = ScoreSession(score, RecordingSynth(), scope)
            val controller = PlaybackController(RecordingSynth(), scope)
            controller.load(score, session)

            controller.toggle()
            eventually { controller.state.value.isPlaying && !session.isHostedEnabled }
            assertFalse(session.isHostedEnabled)

            controller.stop()
            eventually { !controller.state.value.isPlaying && session.isHostedEnabled }
            assertTrue(session.isHostedEnabled)
            assertEquals(0L, session.currentTick)
        } finally {
            scope.cancel()
        }
    }

    private suspend fun eventually(predicate: () -> Boolean) {
        repeat(500) {
            if (predicate()) return
            delay(10)
        }
        assertTrue(predicate(), "condition was not reached")
    }

    private fun score() = Score(
        notes = listOf(
            NoteEntity(0, 60, TrackType.RIGHT_HAND, 0, 2),
            NoteEntity(1, 62, TrackType.RIGHT_HAND, 4, 2),
            NoteEntity(2, 64, TrackType.RIGHT_HAND, 8, 2),
            NoteEntity(3, 65, TrackType.RIGHT_HAND, 12, 2),
        ),
        divisions = 10,
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
