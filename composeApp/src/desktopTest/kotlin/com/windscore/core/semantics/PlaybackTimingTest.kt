package com.windscore.core.semantics

import com.windscore.audio.AudioSynth
import com.windscore.core.engine.ScorePlayer
import com.windscore.core.engine.ScoreSession
import com.windscore.core.engine.SessionLane
import com.windscore.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.*
import kotlin.time.TimeSource

class PlaybackTimingTest {
    @Test
    fun exactReleaseSeekDoesNotRetriggerExpiredNotesOrCutAnOverlappingSustain() = runBlocking {
        val score = Score(listOf(note(0, 60, 0, 10), note(1, 60, 5, 20), note(2, 62, 10)), 100, 120, 40)
        Harness(score).use { h ->
            h.player.seekTo(10)
            h.player.play()
            assertEquals(60, withTimeout(2_000) { h.onsets.receive() })
            assertEquals(62, withTimeout(2_000) { h.onsets.receive() })
            h.step(20, 14)
            assertTrue(h.player.playingPitches.value.contains(60))
            assertFalse(h.onsets.tryReceive().isSuccess)
        }
    }

    @Test
    fun hostedSustainAcrossTempoChangeIsNotTruncatedAtFourSeconds() = runBlocking {
        val origin = TimeSource.Monotonic.markNow()
        val onsets = Channel<Long>(Channel.UNLIMITED)
        val releases = Channel<Long>(Channel.UNLIMITED)
        val synth = object : AudioSynth {
            override fun start() = Unit
            override fun stop() = Unit
            override fun noteOn(channel: Int, pitch: Int, velocity: Int) { onsets.trySend(origin.elapsedNow().inWholeMilliseconds) }
            override fun noteOff(channel: Int, pitch: Int) { releases.trySend(origin.elapsedNow().inWholeMilliseconds) }
        }
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val score = Score(listOf(note(0, 60, 0), note(1, 48, 0, 7, TrackType.LEFT_HAND), note(2, 62, 7)),
            1, 60, 8, tempoChanges = listOf(TempoChange(0, 60.0), TempoChange(3, 120.0)))
        val session = ScoreSession(score, synth, scope, PracticeMode.RIGHT_ONLY)
        try {
            session.onNoteOn(60)
            val duration = withTimeout(6_500) { val start = onsets.receive(); releases.receive() - start }
            assertTrue(duration in 4800..6200, "Expected 3 s + 2 s of hosted sustain, observed $duration ms")
        } finally { session.close(); scope.cancel() }
    }

    @Test
    fun transportIntegratesAcrossTempoChangeAndAppliesSpeedToElapsedTime() = runBlocking {
        Harness(score()).use { h -> h.player.play(); h.step(70, 12) }
        Harness(score()).use { h -> h.player.speedMultiplier = 2.0; h.player.play(); h.step(35, 12) }
    }

    @Test
    fun pauseResumeExcludesPausedWallTimeAndRestoresAnInProgressNote() = runBlocking {
        Harness(score()).use { h ->
            h.player.play()
            assertEquals(60, withTimeout(2_000) { h.onsets.receive() })
            h.step(20, 4)
            h.player.pause()
            h.clock.addAndGet(10_000)
            h.player.play()
            assertEquals(60, withTimeout(2_000) { h.onsets.receive() })
            h.step(20, 8)
            assertTrue(h.player.playing.value)
        }
    }

    @Test
    fun playingSeekAndResetRebaseTheClockWithoutAnOldJobStoppingTheNewOne() = runBlocking {
        Harness(score()).use { h ->
            h.player.play()
            h.step(20, 4)
            h.player.seekTo(60)
            h.step(20, 62)
            assertTrue(h.player.playing.value)
            h.player.reset()
            h.step(20, 4)
            assertTrue(h.player.playing.value)
        }
    }

    @Test
    fun loopPresentationPausesDoNotAdvanceTheNextPassThroughItsTempoMap() = runBlocking {
        Harness(score()).use { h ->
            h.player.loopRange = 0L..20L
            h.player.play()
            h.step(100, 15)
            h.step(50, 20)
            val atBoundary = h.reads.get()
            withTimeout(3_000) { while (h.player.tick.value != 0L) delay(1) }
            h.clock.addAndGet(10_000)
            // Wait for the post-presentation baseline, not just the visible jump to A.
            withTimeout(3_000) { while (h.reads.get() <= atBoundary) delay(1) }
            SessionLane.run { assertEquals(0L, h.player.tick.value) }
            h.step(20, 4)
        }
    }

    @Test
    fun hostedWaitModeUsesTempoMapIntervalsAndSpeedWithoutAdvancingOnWallTimeAlone() = runBlocking {
        for (speed in listOf(1f, 2f)) {
            val origin = TimeSource.Monotonic.markNow()
            val onsets = Channel<Long>(Channel.UNLIMITED)
            val synth = object : AudioSynth {
                override fun start() = Unit
                override fun stop() = Unit
                override fun noteOff(channel: Int, pitch: Int) = Unit
                override fun noteOn(channel: Int, pitch: Int, velocity: Int) { onsets.trySend(origin.elapsedNow().inWholeMilliseconds) }
            }
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val score = Score(listOf(
                note(0, 60, 0), note(1, 48, 4, hand = TrackType.LEFT_HAND),
                note(2, 50, 8, hand = TrackType.LEFT_HAND), note(3, 62, 12),
            ), 4, 3000, 16, tempoChanges = listOf(TempoChange(0, 3000.0), TempoChange(4, 300.0)))
            val session = ScoreSession(score, synth, scope, PracticeMode.RIGHT_ONLY)
            try {
                session.setPracticeSpeed(speed)
                delay(30)
                assertEquals(0L, session.currentTick)
                session.onNoteOn(60)
                val interval = withTimeout(2_000) { val first = onsets.receive(); onsets.receive() - first }
                assertTrue(interval in (120 / speed).toLong()..(500 / speed).toLong(), "Hosted interval at speed $speed: $interval ms")
                SessionLane.run { assertEquals(12L, session.currentTick) }
            } finally { session.close(); scope.cancel() }
        }
    }

    private fun score() = Score(
        listOf(note(0, 60, 0, 50), note(1, 62, 10), note(2, 64, 60)),
        100, 120, 100, tempoChanges = listOf(TempoChange(0, 120.0), TempoChange(10, 60.0)),
    )

    private fun note(id: Int, pitch: Int, tick: Long, duration: Long = 1, hand: TrackType = TrackType.RIGHT_HAND) =
        NoteEntity(id, pitch, hand, tick, duration)

    /** Only wall-clock readings are controlled; the production coroutine scheduler and player execute normally. */
    private class Harness(score: Score) : AutoCloseable {
        val clock = AtomicLong()
        val reads = AtomicInteger()
        val onsets = Channel<Int>(Channel.UNLIMITED)
        private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        private val synth = object : AudioSynth {
            override fun start() = Unit
            override fun stop() = Unit
            override fun noteOff(channel: Int, pitch: Int) = Unit
            override fun noteOn(channel: Int, pitch: Int, velocity: Int) { if (channel == 0) onsets.trySend(pitch) }
        }
        val player = ScorePlayer(score, synth, scope) { reads.incrementAndGet(); clock.get() }.apply { metronomeVolume = 0 }

        suspend fun step(milliseconds: Long, expectedTick: Long) {
            val previousReads = SessionLane.run { clock.addAndGet(milliseconds); reads.get() }
            withTimeout(2_000) { while (reads.get() <= previousReads) delay(1) }
            SessionLane.run { assertEquals(expectedTick, player.tick.value) }
        }

        override fun close() { player.stop(); scope.cancel() }
    }
}
