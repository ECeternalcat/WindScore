package com.windscore.core

import com.windscore.core.model.PracticeMode
import com.windscore.core.practice.AttemptEndReason
import com.windscore.core.practice.AttemptType
import com.windscore.core.practice.HintMode
import com.windscore.core.practice.PracticeAttempt
import com.windscore.core.practice.PracticeContext
import com.windscore.core.practice.PracticeModeType
import com.windscore.core.practice.RawMidiEvent
import com.windscore.core.practice.RawMidiEventType
import java.nio.file.Files
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttemptStorageTest {

    private fun tempDir() = Files.createTempDirectory("attempts_").toFile()

    private fun attempt(id: String, scoreId: String, startedAt: Long, events: Int = 0) = PracticeAttempt(
        attemptId = id,
        sessionId = "session-1",
        context = PracticeContext(
            scoreId = scoreId,
            scoreVersion = 1,
            regionStartTick = 0L,
            regionEndTick = 480L,
            practiceMode = PracticeMode.BOTH,
            hintMode = HintMode.NONE,
            requestedTempo = 120,
            observedTempo = null,
            practiceModeType = PracticeModeType.SHEET_FULL,
            attemptType = AttemptType.PRACTICE,
            midiDeviceId = null,
            startedAt = startedAt,
        ),
        events = (0 until events).map {
            RawMidiEvent(
                offsetMicros = it * 1_000L,
                type = if (it % 2 == 0) RawMidiEventType.NOTE_ON else RawMidiEventType.NOTE_OFF,
                pitch = 60 + it,
                velocity = if (it % 2 == 0) 100 else null,
            )
        },
        endedAt = startedAt + 60_000,
        endReason = AttemptEndReason.COMPLETED,
    )

    @Test
    fun saveAndLoadRoundtripPreservesEventsAndContext() {
        val dir = tempDir()
        try {
            val saved = attempt("a1", "s1", startedAt = 1_000L, events = 4)
            AttemptStorage.save(saved, dir)

            val loaded = AttemptStorage.load("s1", dir)
            assertEquals(listOf(saved.copy(recordingSchemaVersion = 2)), loaded)
            assertEquals(4, loaded.single().events.size)
            assertEquals(3_000L, loaded.single().events.last().offsetMicros)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun loadReturnsNewestFirstAndSeparatesScores() {
        val dir = tempDir()
        try {
            AttemptStorage.save(attempt("a1", "s1", startedAt = 1_000L, events = 1), dir)
            AttemptStorage.save(attempt("a2", "s2", startedAt = 2_000L, events = 1), dir)
            AttemptStorage.save(attempt("a3", "s1", startedAt = 3_000L, events = 1), dir)

            assertEquals(listOf("a3", "a1"), AttemptStorage.load("s1", dir).map { it.attemptId })
            assertEquals(listOf("a2"), AttemptStorage.load("s2", dir).map { it.attemptId })
            assertTrue(AttemptStorage.load("unknown", dir).isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun corruptFileIsSkippedButValidFilesSurvive() {
        val dir = tempDir()
        try {
            AttemptStorage.save(attempt("a1", "s1", startedAt = 1_000L, events = 1), dir)
            val scoreDir = java.io.File(dir, "attempts/s1")
            java.io.File(scoreDir, "broken.cbor").writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))

            val loaded = AttemptStorage.load("s1", dir)
            assertEquals(listOf("a1"), loaded.map { it.attemptId })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun cleanupTempFilesRemovesOnlyStrayTempFiles() {
        val dir = tempDir()
        try {
            AttemptStorage.save(attempt("a1", "s1", startedAt = 1_000L, events = 1), dir)
            val scoreDir = java.io.File(dir, "attempts/s1")
            java.io.File(scoreDir, ".a2.cbor.123456789.tmp").writeBytes(byteArrayOf(1))
            java.io.File(java.io.File(dir, "attempts/s2"), ".x.tmp").apply {
                parentFile.mkdirs()
                writeBytes(byteArrayOf(1))
            }

            assertEquals(2, AttemptStorage.cleanupTempFiles(dir))
            assertEquals(listOf("a1"), AttemptStorage.load("s1", dir).map { it.attemptId })
        } finally {
            dir.deleteRecursively()
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun readsLegacyV1AndPreservesFutureAndCorruptBytes() {
        val dir = tempDir()
        try {
            val scoreDir = java.io.File(dir, "attempts/s1").apply { mkdirs() }
            val legacy = attempt("legacy", "s1", 1, 1)
            val file = java.io.File(scoreDir, "legacy.cbor")
            val bytes = Cbor.encodeToByteArray(AttemptFileEnvelope.serializer(), AttemptFileEnvelope(1, legacy))
            file.writeBytes(bytes)
            val future = java.io.File(scoreDir, "future.cbor")
            val futureBytes = Cbor.encodeToByteArray(AttemptFileEnvelope.serializer(), AttemptFileEnvelope(99, legacy))
            future.writeBytes(futureBytes)
            val corrupt = java.io.File(scoreDir, "broken.cbor").apply { writeBytes(byteArrayOf(0)) }
            val read = AttemptStorage.load("s1", dir).single()
            assertEquals(1, read.recordingSchemaVersion)
            assertNull(read.durationMicros)
            assertNull(read.context.startTick)
            assertNull(read.reachedTick)
            assertTrue(read.carriedInPitches.isEmpty())
            assertTrue(file.readBytes().contentEquals(bytes))
            assertTrue(future.readBytes().contentEquals(futureBytes))
            assertTrue(corrupt.exists())
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun v2EvidenceRoundtripsAndUnsafeComponentsAreRejected() {
        val dir = tempDir()
        try {
            val evidence = attempt("v2", "s1", 1, 2).copy(
                context = attempt("v2", "s1", 1).context.copy(startTick = 240, divisions = 480, speed = 0f, effectiveHostedTempo = null),
                events = listOf(RawMidiEvent(5, RawMidiEventType.NOTE_ON, 60, 99, sessionTick = 240, sessionGroup = 3)),
                durationMicros = 500, reachedTick = 400, lastCompletedGroupTick = 400, boundaryTick = 480,
                carriedInPitches = setOf(61), heldAtBoundaryPitches = setOf(60), unmatchedReleaseCount = 1,
                releasesMayBeOutsideAttempt = true, recordingSchemaVersion = 2,
            )
            AttemptStorage.save(evidence, dir)
            assertEquals(evidence, AttemptStorage.load("s1", dir).single())
            for (invalid in listOf("..", "../escape", "C:\\escape", "a/b", "a\\b", ".hidden")) {
                assertFailsWith<IllegalArgumentException> { AttemptStorage.load(invalid, dir) }
                assertFailsWith<IllegalArgumentException> { AttemptStorage.save(evidence.copy(attemptId = invalid), dir) }
                assertFailsWith<IllegalArgumentException> { AttemptStorage.save(evidence.copy(context = evidence.context.copy(scoreId = invalid)), dir) }
            }
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun cleanupOnMissingDirectoryIsSafe() {
        val dir = tempDir()
        try {
            assertEquals(0, AttemptStorage.cleanupTempFiles(dir))
        } finally {
            dir.deleteRecursively()
        }
    }
}
