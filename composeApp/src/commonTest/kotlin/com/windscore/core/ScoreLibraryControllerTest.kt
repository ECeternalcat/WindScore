package com.windscore.core

import com.windscore.core.model.PracticeMode
import com.windscore.core.model.Score
import com.windscore.core.model.ScoreRecord
import com.windscore.core.model.TrackType
import com.windscore.core.model.NoteEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertIs

class ScoreLibraryControllerTest {
    @Test
    fun openRestoresScoreAndUpdatesRecentList() = runBlocking {
        val source = FakeDataSource()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val controller = ScoreLibraryController(scope, source)
            val score = score("score-a", "A")
            controller.open(score)

            eventually { controller.openedScore.value?.score?.id == "score-a" }

            assertEquals("score-a", controller.openedScore.value?.score?.id)
            assertEquals("A", controller.recentScores.value.first().title)
            assertTrue(source.saved.any { saved -> saved.any { it.id == "score-a" } })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun newerOpenRequestWins() = runBlocking {
        val source = FakeDataSource()
        source.blockId = "score-a"
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val controller = ScoreLibraryController(scope, source)
            controller.open(score("score-a", "A"))
            controller.open(score("score-b", "B"))

            eventually { controller.openedScore.value?.score?.id == "score-b" }

            assertEquals("score-b", controller.openedScore.value?.score?.id)
            assertFalse(controller.opening.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun renameAndDeletePersistUpdatedRecentList() = runBlocking {
        val source = FakeDataSource()
        val score = score("score-a", "A")
        source.recent = listOf(score)
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val controller = ScoreLibraryController(scope, source)
            eventually { controller.recentScores.value == listOf(score) }

            controller.rename(score, "Renamed")
            eventually { source.saved.lastOrNull()?.firstOrNull()?.title == "Renamed" }
            assertEquals("Renamed", controller.recentScores.value.single().title)

            controller.delete(score.copy(title = "Renamed"))
            eventually { controller.recentScores.value.isEmpty() }
            eventually { source.saved.lastOrNull()?.isEmpty() == true }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun appendedHistoryUpdatesAfterOpenedEventIsConsumed() = runBlocking {
        val source = FakeDataSource()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val controller = ScoreLibraryController(scope, source)
            controller.open(score("score-a", "A"))
            eventually { controller.openedScore.value != null }
            controller.consumeOpenedScore()
            val record = ScoreRecord(
                timestamp = 1,
                percent = 90,
                cleanHits = 9,
                totalNotes = 10,
                wrongPresses = 1,
                practiceMode = PracticeMode.BOTH,
            )

            controller.appendHistory("score-a", record)
            eventually { controller.scoreHistory.value == listOf(record) }

            assertEquals(listOf(record), controller.scoreHistory.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun failedOpenPublishesDiagnosticAndClearsOpeningState() = runBlocking {
        val source = FakeDataSource().apply { xmlFailure = IllegalStateException("missing XML") }
        val diagnostics = DiagnosticController()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val expected = async(start = CoroutineStart.UNDISPATCHED) { diagnostics.events.first() }
            val controller = ScoreLibraryController(scope, source, diagnostics)
            controller.open(score("score-a", "A"))

            val diagnostic = expected.await()
            val library = assertIs<AppDiagnostic.Library>(diagnostic)
            assertEquals(LibraryDiagnosticOperation.OPEN, library.operation)
            eventually { !controller.opening.value }
            assertTrue(controller.openedScore.value == null)
        } finally {
            scope.cancel()
        }
    }

    private suspend fun eventually(predicate: () -> Boolean) {
        repeat(100) {
            if (predicate()) return
            delay(10)
        }
        assertTrue(predicate(), "condition was not reached")
    }

    private fun score(id: String, title: String) = Score(
        id = id,
        title = title,
        notes = listOf(NoteEntity(0, 60, TrackType.RIGHT_HAND, 0, 4)),
        divisions = 4,
        tempo = 120,
        totalTicks = 4,
        rawXml = null,
        supportsSheetView = true,
    )

    private class FakeDataSource : ScoreLibraryDataSource {
        var recent = emptyList<Score>()
        var blockId: String? = null
        var xmlFailure: Throwable? = null
        val saved = mutableListOf<List<Score>>()

        override suspend fun loadRecentScores(): List<Score> = recent

        override suspend fun loadScoreXml(scoreId: String): String? {
            xmlFailure?.let { throw it }
            while (scoreId == blockId) delay(25)
            return "<score-partwise />"
        }

        override suspend fun loadScoreHistory(scoreId: String): List<ScoreRecord> = emptyList()

        override suspend fun saveRecentScores(scores: List<Score>) {
            saved += scores
        }

        override suspend fun appendScoreRecord(scoreId: String, record: ScoreRecord): List<ScoreRecord> = listOf(record)
    }
}
