package com.windscore.core.collection

import com.windscore.core.model.PracticeMode
import com.windscore.core.practice.AttemptEndReason
import com.windscore.core.practice.AttemptType
import com.windscore.core.practice.HintMode
import com.windscore.core.practice.PracticeAttempt
import com.windscore.core.practice.PracticeContext
import com.windscore.core.practice.PracticeModeType
import com.windscore.core.practice.RawMidiEvent
import com.windscore.core.practice.RawMidiEventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString

class AttemptExportTest {
    private fun attempt(
        id: String,
        session: String,
        started: Long = 1_700_000_123_456L,
        scoreId: String = "PRIVATE_SCORE_TITLE_SENTINEL_HASH_PATH",
        scoreVersion: Int = 9281,
        divisions: Int? = 480,
    ) = PracticeAttempt(
        attemptId = id,
        sessionId = session,
        context = PracticeContext(
            scoreId = scoreId,
            scoreVersion = scoreVersion,
            regionStartTick = 100,
            regionEndTick = 500,
            practiceMode = PracticeMode.BOTH,
            hintMode = HintMode.NONE,
            requestedTempo = 72,
            practiceModeType = PracticeModeType.SHEET_FULL,
            attemptType = AttemptType.PRACTICE,
            midiDeviceId = "PRIVATE_DEVICE_SENTINEL",
            startedAt = started,
            startTick = 140,
            divisions = divisions,
            speed = .75f,
            effectiveHostedTempo = 72,
        ),
        events = listOf(
            RawMidiEvent(12_345, RawMidiEventType.NOTE_ON, 60, 87, "PRIVATE_SOURCE_SENTINEL", 145, 2),
            RawMidiEvent(90_000, RawMidiEventType.NOTE_OFF, 60, null, "PRIVATE_SOURCE_SENTINEL", 150, 2),
        ),
        endedAt = 1_700_000_130_000L,
        endReason = AttemptEndReason.COMPLETED,
        durationMicros = 6_543_210,
        reachedTick = 480,
        lastCompletedGroupTick = 450,
        boundaryTick = 500,
        carriedInPitches = setOf(64),
        heldAtBoundaryPitches = setOf(67),
        unmatchedReleaseCount = 1,
        releasesMayBeOutsideAttempt = true,
        recordingSchemaVersion = 2,
    )

    @Test fun summaryIsAllowlistedAndExcludesRawNoteDetailsAndSensitiveIdentity() {
        val json = AttemptExport.serialize(listOf(attempt("PRIVATE_ATTEMPT_SENTINEL", "PRIVATE_SESSION_SENTINEL")), false, "1.0.0")
        listOf("PRIVATE_SCORE_TITLE_SENTINEL_HASH_PATH", "PRIVATE_DEVICE_SENTINEL", "PRIVATE_SOURCE_SENTINEL",
            "PRIVATE_ATTEMPT_SENTINEL", "PRIVATE_SESSION_SENTINEL", "1700000123456", "1700000130000",
            "startedAt", "endedAt", "midiDeviceId", "\"source\":", "pitch", "NOTE_ON").forEach {
            assertFalse(json.contains(it), "summary leaked $it: $json")
        }
        assertTrue(json.contains("\"scoreVersion\": 9281"))
        assertTrue(json.contains("\"divisions\": 480"))
        assertTrue(json.contains("\"eventCount\": 2"))
        assertTrue(json.contains("\"noteOnCount\": 1"))
        assertTrue(json.contains("boundary_may_exclude_later_releases"))
        assertTrue(json.contains("attempt_started_inside_selected_region"))
        assertFalse(json.contains("\"events\""))
    }

    @Test fun rawOptInContainsMinimizedRelativeEventsButNoSensitiveIdentity() {
        val json = AttemptExport.serialize(listOf(attempt("SECRET_ATTEMPT", "SECRET_SESSION")), true, "1.0")
        assertTrue(json.contains("\"containsRawMidi\": true"))
        assertTrue(json.contains("\"offsetMicros\": 12345"))
        assertTrue(json.contains("\"sessionOffsetTicks\": 45"))
        assertTrue(json.contains("\"pitch\": 60"))
        listOf("PRIVATE_SCORE_TITLE_SENTINEL_HASH_PATH", "PRIVATE_DEVICE_SENTINEL", "PRIVATE_SOURCE_SENTINEL",
            "SECRET_ATTEMPT", "SECRET_SESSION", "1700000123456", "1700000130000", "startedAt", "endedAt",
            "scoreId", "sessionId", "attemptId", "midiDeviceId", "PRIVATE").forEach {
            assertFalse(json.contains(it), "raw export leaked $it: $json")
        }
        assertTrue(json.contains("session-1"))
        assertTrue(json.contains("attempt-1"))
    }

    @Test fun aliasesVersionsAndDivisionsAreStableAcrossScoresAndSelectionOrder() {
        val scoreB = attempt("attempt-b", "shared-session", scoreId = "PRIVATE_SCORE_B", scoreVersion = 7, divisions = 960)
        val scoreA2 = attempt("attempt-a2", "shared-session", scoreId = "PRIVATE_SCORE_A", scoreVersion = 4, divisions = 480)
        val scoreA = attempt("attempt-a", "shared-session", scoreId = "PRIVATE_SCORE_A", scoreVersion = 3, divisions = null)
        val selected = listOf(scoreB, scoreA2, scoreA)
        val first = AttemptExport.build(selected, true, "1.0")
        val second = AttemptExport.build(selected.reversed(), true, "1.0")
        assertEquals(AttemptExport.json.encodeToString(first), AttemptExport.json.encodeToString(second))
        assertEquals(listOf("score-1", "score-1", "score-2"), first.attempts.map { it.scoreAlias })
        assertEquals(listOf("session-1", "session-1", "session-2"), first.attempts.map { it.sessionAlias })
        assertEquals(listOf("attempt-1", "attempt-2", "attempt-3"), first.attempts.map { it.attemptAlias })
        assertEquals(listOf(3, 4, 7), first.attempts.map { it.scoreVersion })
        assertEquals(listOf(null, 480, 960), first.attempts.map { it.divisions })
        val json = AttemptExport.json.encodeToString(first)
        listOf("PRIVATE_SCORE_A", "PRIVATE_SCORE_B", "scoreId").forEach { assertFalse(json.contains(it)) }
        assertEquals("windscore-practice-collection.json", AttemptExport.suggestedFilename())
        assertTrue(AttemptExport.build(listOf(scoreA), false, "x").attempts.single().events == null)
        assertNull(AttemptExport.build(emptyList(), true, "x").attempts.firstOrNull())
    }
}
