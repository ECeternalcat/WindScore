package com.windscore.core.semantics

import com.windscore.audio.AudioSynth
import com.windscore.core.engine.JudgmentEngine
import com.windscore.core.engine.ScorePlayer
import com.windscore.core.model.NoteEntity
import com.windscore.core.model.Score
import com.windscore.core.model.TrackType
import com.windscore.core.parser.MusicXmlParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/** Desired musical behavior: these are deliberately failing regressions, not expected-failure tests. */
class MusicSemanticsRegressionTest {
    @Test
    fun divisionsChangesPreserveQuarterNoteCoordinates() {
        val score = parse("""
            <measure number="1">
              ${attributes(4)}
              ${note("C", 4)}
            </measure>
            <measure number="2">
              <attributes><divisions>8</divisions></attributes>
              ${note("D", 8)}
            </measure>
        """)
        println("divisions=${score.divisions}, events=${score.notes}, totalTicks=${score.totalTicks}")
        // Permit any canonical resolution; do not prescribe milliseconds or the final XML divisions.
        assertEquals(listOf(0L, score.divisions.toLong()), score.notes.map { it.startTick })
        assertEquals(listOf(score.divisions.toLong(), score.divisions.toLong()), score.notes.map { it.durationTick })
        assertEquals(2L * score.divisions, score.totalTicks)
    }

    @Test
    fun simultaneousSamePitchTiesInDifferentVoicesRemainIndependent() {
        val score = parse("""
            <measure number="1">
              ${attributes(4)}
              ${note("C", 4, voice = 1, tie = "start")}
              <backup><duration>4</duration></backup>
              ${note("C", 4, voice = 2, tie = "start")}
            </measure>
            <measure number="2">
              ${note("C", 4, voice = 1, tie = "stop")}
              <backup><duration>4</duration></backup>
              ${note("C", 4, voice = 2, tie = "stop")}
            </measure>
        """)
        println("independent voice ties: ${score.notes}")
        assertEquals(listOf(0L to 8L, 0L to 8L), score.notes.map { it.startTick to it.durationTick })
        assertEquals(2, score.notes.map { it.id }.toSet().size)
    }

    @Test
    fun tieChangingStaffInTheSameVoiceProducesOneSustainedEvent() {
        val score = parse("""
            <measure number="1">
              ${attributes(4)}
              ${note("C", 4, voice = 1, staff = 1, tie = "start")}
            </measure>
            <measure number="2">
              ${note("C", 4, voice = 1, staff = 2, tie = "stop")}
            </measure>
        """)
        println("cross-staff tie: ${score.notes}")
        // Staff is visual placement. This test deliberately does not prescribe practice-hand assignment.
        assertEquals(listOf(0L to 8L), score.notes.map { it.startTick to it.durationTick })
        assertEquals(60, score.notes.single().pitch)
    }

    @Test
    fun tempoChangeAffectsWallTimeWithoutChangingScoreTicks() = runBlocking {
        val score = parse("""
            <measure number="1">
              ${attributes(4)}
              <direction><sound tempo="120"/></direction>
              ${note("C", 4)}
            </measure>
            <measure number="2">
              <direction><sound tempo="30"/></direction>
              ${note("D", 4)}
            </measure>
            <measure number="3">${note("E", 4)}</measure>
        """)
        assertEquals(listOf(0L, score.divisions.toLong(), 2L * score.divisions), score.notes.map { it.startTick })
        val synth = RecordingSynth()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val player = ScorePlayer(score, synth, scope).apply { metronomeVolume = 0 }
        try {
            player.play()
            val events = withTimeout(15_000) { List(3) { synth.noteOns.receive() } }
            assertEquals(listOf(60, 62, 64), events.map { it.pitch })
            val firstMs = (events[1].micros - events[0].micros) / 1000.0
            val secondMs = (events[2].micros - events[1].micros) / 1000.0
            println("tempo=120 -> 30, ticks=${score.notes.map { it.startTick }}, onset intervals ms=[$firstMs, $secondMs]")
            // Broad scheduling tolerance, but clearly separate 500 ms from the current 2000 ms.
            assertTrue(firstMs in 300.0..900.0, "First quarter at 120 BPM: expected about 500 ms, observed $firstMs ms")
            assertTrue(secondMs in 1600.0..2600.0, "Second quarter at 30 BPM: expected about 2000 ms, observed $secondMs ms")
        } finally {
            player.stop()
            scope.cancel()
        }
    }

    @Test
    fun playbackLoopNeverTriggersANoteAtExclusiveBoundaryB() = runBlocking {
        val score = Score(
            notes = listOf(event(0, 60, 0), event(1, 62, 4), event(2, 64, 8)),
            divisions = 10, tempo = 600, totalTicks = 12,
        )
        val synth = RecordingSynth()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val player = ScorePlayer(score, synth, scope).apply {
            metronomeVolume = 0
            loopRange = 0L..4L // Musical interval [0, 4), despite LongRange's syntax.
        }
        try {
            player.play()
            val observed = mutableListOf<Int>()
            // Observe two visits to A, proving an actual loop wrap rather than a short sleep.
            withTimeout(8_000) {
                do { observed += synth.noteOns.receive().pitch } while (observed.count { it == 60 } < 2)
            }
            println("loop [0,4), note-ons through second A: $observed")
            assertEquals(listOf(60, 60), observed, "B and later notes must not sound inside [A,B)")
        } finally {
            player.stop()
            scope.cancel()
        }
    }

    @Test
    fun emptyPracticeLoopDoesNotAdvertiseOrJudgeANoteAtB() {
        val score = Score(
            notes = listOf(event(0, 60, 0), event(1, 62, 8), event(2, 64, 12)),
            divisions = 4, tempo = 120, totalTicks = 16,
        )
        val engine = JudgmentEngine(score)
        engine.setLoop(4, 8) // No note starts in [4,8); the next note belongs outside the loop.
        println("empty loop [4,8): tick=${engine.currentTick}, pending=${engine.pendingPitches()}")
        assertEquals(emptySet<Int>(), engine.pendingPitches(), "An empty loop must not request the note at B")
        val before = engine.hitNoteIds()
        engine.onNoteOn(62)
        assertEquals(before, engine.hitNoteIds(), "Input must not complete a note outside the loop")
        assertTrue(engine.drainCompletedGroups().isEmpty())
    }

    private fun parse(measures: String): Score = MusicXmlParser.parseXml("""
        <score-partwise version="4.0">
          <part-list><score-part id="P1"><part-name>Piano</part-name></score-part></part-list>
          <part id="P1">$measures</part>
        </score-partwise>
    """.trimIndent().toByteArray(Charsets.UTF_8))

    private fun attributes(divisions: Int) = """
        <attributes><divisions>$divisions</divisions><time><beats>1</beats><beat-type>4</beat-type></time>
          <staves>2</staves><clef number="1"><sign>G</sign><line>2</line></clef>
          <clef number="2"><sign>F</sign><line>4</line></clef></attributes>
    """

    private fun note(step: String, duration: Int, voice: Int = 1, staff: Int = 1, tie: String? = null): String = """
        <note><pitch><step>$step</step><octave>4</octave></pitch><duration>$duration</duration>
          ${tie?.let { "<tie type=\"$it\"/>" } ?: ""}
          <voice>$voice</voice><type>quarter</type><staff>$staff</staff>
          ${tie?.let { "<notations><tied type=\"$it\"/></notations>" } ?: ""}
        </note>
    """

    private fun event(id: Int, pitch: Int, tick: Long) = NoteEntity(id, pitch, TrackType.RIGHT_HAND, tick, 1)

    private data class NoteOn(val pitch: Int, val micros: Long)

    private class RecordingSynth : AudioSynth {
        private val origin = TimeSource.Monotonic.markNow()
        val noteOns = Channel<NoteOn>(Channel.UNLIMITED)
        override fun start() = Unit
        override fun stop() = Unit
        override fun noteOff(channel: Int, pitch: Int) = Unit
        override fun noteOn(channel: Int, pitch: Int, velocity: Int) {
            if (channel == 0) check(noteOns.trySend(NoteOn(pitch, origin.elapsedNow().inWholeMicroseconds)).isSuccess)
        }
    }
}
