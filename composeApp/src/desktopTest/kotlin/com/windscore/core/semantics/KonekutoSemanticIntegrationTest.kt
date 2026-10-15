package com.windscore.core.semantics

import com.windscore.audio.AudioSynth
import com.windscore.core.engine.*
import com.windscore.core.loadScore
import com.windscore.core.model.TrackSelection
import com.windscore.core.model.TrackType
import com.windscore.core.model.PracticeMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.*
import kotlin.time.TimeSource

/** Expected events/times come from the independent Python fraction-and-interval-graph oracle. */
class KonekutoSemanticIntegrationTest {
    private val reference = Json.parseToJsonElement(javaClass.getResourceAsStream("/konekuto-reference.json")!!
        .bufferedReader().use { it.readText() }).jsonObject
    private val expected = reference.getValue("events").jsonArray.map { it.jsonObject }
    private fun JsonObject.long(name: String) = getValue(name).jsonPrimitive.long
    private fun JsonObject.int(name: String) = getValue(name).jsonPrimitive.int
    private fun JsonObject.double(name: String) = getValue(name).jsonPrimitive.double

    @Test
    fun sourceIdentityAndEveryParsedEventMatchIndependentReference() {
        val bytes = javaClass.getResourceAsStream("/konekuto-claris.mxl")!!.use { it.readBytes() }
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(reference.getValue("sourceSha256").jsonPrimitive.content, sha)
        val android = listOf(File("src/androidMain/assets/konekuto-claris.mxl"), File("composeApp/src/androidMain/assets/konekuto-claris.mxl"))
            .first { it.exists() }
        assertContentEquals(bytes, android.readBytes())
        val score = loadScore()
        assertEquals(reference.int("divisions"), score.divisions)
        assertEquals(reference.long("totalTicks"), score.totalTicks)
        val actual = score.notes.map { listOf(it.pitch.toLong(), it.startTick, it.durationTick,
            if (it.track == TrackType.LEFT_HAND) 2L else 1L) }.groupingBy { it }.eachCount()
        val wanted = expected.map { listOf(it.long("pitch"), it.long("startTick"), it.long("durationTick"), it.long("staff")) }
            .groupingBy { it }.eachCount()
        assertEquals(wanted, actual)
        assertEquals(reference.int("eventCount"), score.notes.map { it.id }.toSet().size)
    }

    @Test
    fun tempoMapAndMeasure165DurationMatchIndependentElapsedTime() {
        val score = loadScore()
        assertEquals(reference.getValue("tempoChanges").jsonArray.map { it.jsonObject.let { t -> t.long("tick") to t.double("bpm") } },
            score.tempoChanges.map { it.tick to it.bpm })
        val timing = ScoreTiming(score)
        assertEquals(225600.0, timing.millisecondsAt(2632.0), 1e-6)
        assertEquals(4 * 60000.0 / 58, timing.millisecondsBetween(2632, 2648), 1e-6)
        assertEquals(reference.double("totalMilliseconds"), timing.millisecondsAt(score.totalTicks.toDouble()), 1e-6)
        assertEquals(setOf(30, 42, 58, 66), score.notes.filter { it.startTick == 2632L }.map { it.pitch }.toSet())
    }

    @Test
    fun bothHandsPracticeVisitsEveryIndependentOnsetAndNote() {
        val score = loadScore()
        val engine = JudgmentEngine(score)
        for ((tick, notes) in expected.groupBy { it.long("startTick") }.toSortedMap()) {
            assertEquals(tick, engine.currentTick)
            val pitches = notes.map { it.int("pitch") }.toSet()
            assertEquals(pitches, engine.pendingPitches())
            pitches.forEach { engine.onNoteOn(it) }
            pitches.forEach { engine.onNoteOff(it) }
        }
        assertTrue(engine.isFinished)
        assertEquals(reference.int("groupCount"), engine.groupCount)
        assertEquals(reference.int("eventCount"), engine.cleanHitCount())
        assertEquals(score.notes.map { it.id }.toSet(), engine.hitNoteIds())
        assertEquals(100, engine.scorePercent())
    }

    @Test
    fun singleHandPracticeHostsExactlyTheIndependentOtherHandEvents() {
        for (selection in listOf(TrackSelection.RIGHT_HAND, TrackSelection.LEFT_HAND)) {
            val score = loadScore()
            val engine = JudgmentEngine(score, selection)
            val hosted = mutableListOf<com.windscore.core.model.NoteEntity>()
            val activeStaff = if (selection == TrackSelection.RIGHT_HAND) 1 else 2
            hosted += engine.arrive()
            var steps = 0
            while (!engine.isFinished && steps++ < 2000) {
                val wanted = expected.filter { it.long("startTick") == engine.currentTick && it.int("staff") == activeStaff }
                    .map { it.int("pitch") }.toSet()
                assertEquals(wanted, engine.pendingPitches())
                if (wanted.isEmpty()) hosted += engine.advanceHosted() else {
                    wanted.forEach { hosted += engine.onNoteOn(it) }
                    wanted.forEach { engine.onNoteOff(it) }
                }
            }
            assertTrue(engine.isFinished)
            assertEquals(expected.filter { it.int("staff") != activeStaff }.map { listOf(it.long("pitch"), it.long("startTick"), it.long("durationTick")) }
                .groupingBy { it }.eachCount(), hosted.map { listOf(it.pitch.toLong(), it.startTick, it.durationTick) }.groupingBy { it }.eachCount())
            assertEquals(100, engine.scorePercent())
        }
    }

    @Test
    fun completePlaybackMatchesIndependentAudioEventOrderAndTiming() = runBlocking {
        val score = loadScore()
        val virtual = AtomicLong()
        val lastRead = AtomicLong()
        val actual = mutableListOf<Pair<String, Double>>()
        val synth = recorder { event -> actual += event to lastRead.get().toDouble() }
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val player = ScorePlayer(score, synth, scope) { virtual.getAndAdd(100).also { lastRead.set(it) } }.apply { metronomeVolume = 0 }
        try {
            player.play()
            withTimeout(40_000) { player.finished.first { it } }
            val wanted = referenceAudioEvents()
            SessionLane.run {
                assertEquals(wanted.map { it.first }, actual.map { it.first })
                wanted.zip(actual).forEach { (expectedEvent, actualEvent) ->
                    val lateness = actualEvent.second - expectedEvent.second
                    assertTrue(lateness in -0.001..100.001, "${actualEvent.first}: lateness $lateness ms")
                }
                assertTrue(player.playingPitches.value.isEmpty())
            }
        } finally { player.stop(); scope.cancel() }
    }

    @Test
    fun actualFinalChordPlaybackUses58BpmAndReleasesAllFourNotes() = runBlocking {
        val score = loadScore()
        val origin = TimeSource.Monotonic.markNow()
        val actual = mutableListOf<Pair<String, Double>>()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val player = ScorePlayer(score, recorder { actual += it to origin.elapsedNow().inWholeMicroseconds / 1000.0 }, scope)
            .apply { metronomeVolume = 0 }
        try {
            player.seekTo(2632)
            player.play()
            withTimeout(7_000) { player.finished.first { it } }
            SessionLane.run {
                val ons = actual.filter { it.first.startsWith("on:") }
                val offs = actual.filter { it.first.startsWith("off:") }
                assertEquals(setOf("on:30", "on:42", "on:58", "on:66"), ons.map { it.first }.toSet())
                assertEquals(4, ons.size)
                assertEquals(setOf("off:30", "off:42", "off:58", "off:66"), offs.map { it.first }.toSet())
                val duration = offs.last().second - ons.first().second
                println("Measure 165 actual callback duration: $duration ms; independent reference: ${4 * 60000.0 / 58}")
                assertTrue(duration in 3900.0..4400.0)
            }
        } finally { player.stop(); scope.cancel() }
    }

    @Test
    fun finalChordWaitModeHostsTheOtherHandAtReferenceTempoAndSpeed() = runBlocking {
        for ((mode, speed) in listOf(PracticeMode.RIGHT_ONLY to 1f, PracticeMode.LEFT_ONLY to 2f)) {
            val origin=TimeSource.Monotonic.markNow()
            val actual=mutableListOf<Pair<String,Double>>()
            val scope=CoroutineScope(Dispatchers.Default+SupervisorJob())
            val session=ScoreSession(loadScore(),recorder { actual += it to origin.elapsedNow().inWholeMicroseconds/1000.0 },scope,mode)
            val active=if(mode==PracticeMode.RIGHT_ONLY) listOf(58,66) else listOf(30,42)
            val hosted=if(mode==PracticeMode.RIGHT_ONLY) listOf(30,42) else listOf(58,66)
            try {
                session.setPracticeSpeed(speed)
                session.seekTo(2632)
                assertEquals(active.toSet(),session.pendingPitches)
                session.onNoteOn(active.first())
                delay(200)
                assertEquals(2632L,session.currentTick, "waiting must not advance with wall time")
                assertEquals(active.toSet(), session.pendingPitches) // UI advertises the entire waiting group
                assertEquals(setOf(active.first()), session.heldPitches)
                SessionLane.run { assertEquals(hosted.map { "on:$it" }.toSet(),actual.map { it.first }.toSet()) }
                session.onNoteOn(active.last())
                active.forEach(session::onNoteOff)
                assertTrue(session.finished)
                withTimeout(6500) { while(SessionLane.run { actual.count { it.first.startsWith("off:") } }<2) delay(10) }
                SessionLane.run {
                    assertEquals(hosted.map { "off:$it" }.toSet(),actual.filter { it.first.startsWith("off:") }.map { it.first }.toSet())
                    val duration=actual.last().second-actual.first().second
                    val wanted=(4*60000.0/58)/speed
                    println("Measure 165 hosted $mode speed $speed: $duration ms; reference $wanted")
                    assertEquals(wanted,duration,180.0)
                }
            } finally { session.close(); scope.cancel() }
        }
    }

    private fun referenceAudioEvents(): List<Pair<String, Double>> {
        data class Event(val tick: Long, val on: Boolean, val pitch: Int, val milliseconds: Double)
        val raw = expected.flatMap { listOf(Event(it.long("startTick"), true, it.int("pitch"), it.double("onsetMilliseconds")),
            Event(it.long("startTick") + it.long("durationTick"), false, it.int("pitch"), it.double("releaseMilliseconds"))) }
            .sortedWith(compareBy<Event> { it.tick }.thenBy { it.on })
        val active = mutableMapOf<Int, Int>()
        return buildList {
            for (event in raw) {
                if (event.on) {
                    active[event.pitch] = (active[event.pitch] ?: 0) + 1
                    add("on:${event.pitch}" to event.milliseconds)
                } else {
                    val remaining = active.getValue(event.pitch) - 1
                    if (remaining == 0) { active.remove(event.pitch); add("off:${event.pitch}" to event.milliseconds) }
                    else active[event.pitch] = remaining
                }
            }
        }
    }

    private fun recorder(record: (String) -> Unit) = object : AudioSynth {
        override fun start() = Unit
        override fun stop() = Unit
        override fun noteOn(channel: Int, pitch: Int, velocity: Int) { if (channel == 0) SessionLane.run { record("on:$pitch") } }
        override fun noteOff(channel: Int, pitch: Int) { if (channel == 0) SessionLane.run { record("off:$pitch") } }
    }
}
