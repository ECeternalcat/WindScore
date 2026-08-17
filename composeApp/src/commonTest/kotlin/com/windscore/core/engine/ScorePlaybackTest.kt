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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScorePlaybackTest {
    @Test
    fun seekKeepsEventsAtTargetTick() = runBlocking {
        val synth = RecordingSynth()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val score = scoreOf(
                note(0, 60, 0, 2),
                note(1, 64, 4, 2),
                totalTicks = 8,
            )
            val player = ScorePlayer(score, synth, scope)

            player.seekTo(4)
            player.play()
            delay(100)

            assertTrue(synth.events.contains("on:0:64"))
            assertFalse(synth.events.contains("on:0:60"))
            player.stop()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun overlappingSamePitchIsReleasedOnlyAfterLastNote() = runBlocking {
        val synth = RecordingSynth()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val score = scoreOf(
                note(0, 60, 0, 10),
                note(1, 60, 5, 10),
                totalTicks = 18,
            )
            val player = ScorePlayer(score, synth, scope)

            player.play()
            delay(250)

            assertEquals(2, synth.events.count { it == "on:0:60" })
            assertEquals(1, synth.events.count { it == "off:0:60" })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun stoppingWithLoopActiveReturnsToLoopStart() {
        val synth = RecordingSynth()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val player = ScorePlayer(
                scoreOf(
                    note(0, 60, 0, 2),
                    note(1, 64, 4, 2),
                    note(2, 67, 8, 2),
                    totalTicks = 12,
                ),
                synth,
                scope,
            )
            player.loopRange = 4L..8L
            player.seekTo(7)

            player.stop()

            assertEquals(4L, player.tick.value)
            assertFalse(player.playing.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun loopConstrainsSeekAndStepOperations() {
        val synth = RecordingSynth()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val player = ScorePlayer(
                scoreOf(
                    note(0, 60, 0, 2),
                    note(1, 62, 4, 2),
                    note(2, 64, 8, 2),
                    note(3, 65, 12, 2),
                    totalTicks = 16,
                ),
                synth,
                scope,
            )
            player.loopRange = 4L..12L

            player.seekTo(0)
            assertEquals(4L, player.tick.value)
            player.stepBackward()
            assertEquals(4L, player.tick.value)

            player.seekTo(16)
            assertEquals(12L, player.tick.value)
            player.stepForward()
            assertEquals(12L, player.tick.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun practiceLoopConstrainsSeekAndStepOperations() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val session = ScoreSession(
                scoreOf(
                    note(0, 60, 0, 2),
                    note(1, 62, 4, 2),
                    note(2, 64, 8, 2),
                    note(3, 65, 12, 2),
                    totalTicks = 16,
                ),
                RecordingSynth(),
                scope,
            )
            session.setLoop(4L, 12L)

            session.seekTo(0)
            assertEquals(4L, session.currentTick)
            session.stepBackward()
            assertEquals(4L, session.currentTick)

            session.seekTo(16)
            assertEquals(12L, session.currentTick)
            session.stepForward()
            assertEquals(12L, session.currentTick)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun closingSessionCancelsFutureHostedGroups() = runBlocking {
        val synth = RecordingSynth()
        val parentScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val score = scoreOf(
                note(0, 60, 0, 1, TrackType.RIGHT_HAND),
                note(1, 48, 1, 1, TrackType.LEFT_HAND),
                note(2, 50, 20, 1, TrackType.LEFT_HAND),
                totalTicks = 24,
            )
            val session = ScoreSession(score, synth, parentScope, PracticeMode.RIGHT_ONLY)

            session.onNoteOn(60)
            delay(20)
            assertTrue(synth.events.contains("on:0:48"))
            session.close()
            delay(250)

            assertFalse(synth.events.contains("on:0:50"))
        } finally {
            parentScope.cancel()
        }
    }

    private fun note(
        id: Int,
        pitch: Int,
        start: Long,
        duration: Long,
        track: TrackType = TrackType.RIGHT_HAND,
    ) = NoteEntity(id, pitch, track, start, duration)

    private fun scoreOf(vararg notes: NoteEntity, totalTicks: Long) = Score(
        notes = notes.toList(),
        divisions = 10,
        tempo = 600,
        totalTicks = totalTicks,
    )

    private class RecordingSynth : AudioSynth {
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())

        override fun start() = Unit
        override fun noteOn(channel: Int, pitch: Int, velocity: Int) {
            events += "on:$channel:$pitch"
        }
        override fun noteOff(channel: Int, pitch: Int) {
            events += "off:$channel:$pitch"
        }
        override fun stop() = Unit
    }
}
