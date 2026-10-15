package com.windscore.core.semantics

import com.windscore.audio.AudioSynth
import com.windscore.core.engine.JudgmentEngine
import com.windscore.core.engine.ScorePlayer
import com.windscore.core.model.NoteEntity
import com.windscore.core.model.Score
import com.windscore.core.model.TrackType
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.test.*

class LoopBoundaryTest {
    @Test
    fun schedulingOvershootCannotSoundBOrLaterAndCutsSustainsAtB() = runBlocking {
        val score = Score(
            listOf(note(0, 60, 0, 200), note(1, 62, 10), note(2, 64, 11)),
            divisions = 100, tempo = 6000, totalTicks = 300,
        )
        val events = Channel<String>(Channel.UNLIMITED)
        val synth = object : AudioSynth {
            override fun start() = Unit
            override fun stop() = Unit
            override fun noteOn(channel: Int, pitch: Int, velocity: Int) { events.trySend("on:$pitch") }
            override fun noteOff(channel: Int, pitch: Int) { events.trySend("off:$pitch") }
        }
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val player = ScorePlayer(score, synth, scope).apply { loopRange = 0L..10L; metronomeVolume = 0 }
        try {
            player.play()
            val observed = mutableListOf<String>()
            withTimeout(8_000) {
                do { observed += events.receive() } while (observed.count { it == "on:60" } < 2)
            }
            assertEquals(listOf("on:60", "off:60", "on:60"), observed)
        } finally { player.stop(); scope.cancel() }
    }

    @Test
    fun loopBeyondLastNoteIsEmptyAndCanBeCleared() {
        val engine = JudgmentEngine(Score(listOf(note(0, 60, 0)), 4, 120, 32))
        engine.setLoop(20, 24)
        assertEquals(24L, engine.currentTick)
        assertTrue(engine.isFinished)
        assertTrue(engine.pendingPitches().isEmpty())
        engine.onNoteOn(60)
        assertTrue(engine.hitNoteIds().isEmpty())
        assertTrue(engine.drainCompletedGroups().isEmpty())
        engine.clearLoop()
        engine.reset()
        assertEquals(setOf(60), engine.pendingPitches())
    }

    @Test
    fun seekToExclusiveBDoesNotRequestOrCompleteTheGroupAtB() {
        val engine = JudgmentEngine(Score(listOf(note(0, 60, 0), note(1, 62, 8)), 4, 120, 16))
        engine.setLoop(0, 8)
        engine.seekTo(8)
        assertEquals(8L, engine.currentTick)
        assertTrue(engine.pendingPitches().isEmpty())
        val hits = engine.hitNoteIds()
        engine.onNoteOn(62)
        assertEquals(hits, engine.hitNoteIds())
        assertFalse(1 in engine.hitNoteIds())
        assertTrue(engine.drainCompletedGroups().isEmpty())
    }

    @Test
    fun zeroLengthLoopRemainsDisabledAndResetClearsStaleProgressEvidence() {
        val engine = JudgmentEngine(Score(listOf(note(0, 60, 0), note(1, 62, 8)), 4, 120, 16))
        engine.onNoteOn(60)
        engine.setLoop(4, 4)
        assertFalse(engine.isLoopActive)
        assertEquals(0L, engine.currentTick)
        assertEquals(setOf(60), engine.pendingPitches())
        assertTrue(engine.drainCompletedGroups().isEmpty())
    }

    private fun note(id: Int, pitch: Int, tick: Long, duration: Long = 1) =
        NoteEntity(id, pitch, TrackType.RIGHT_HAND, tick, duration)
}
