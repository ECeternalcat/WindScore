package com.windscore.core

import com.windscore.core.model.PracticeMode
import com.windscore.core.model.ScoreRecord
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoreHistoryStorageTest {

    private fun tempDir() = Files.createTempDirectory("scorehistory_").toFile()

    private fun record(ts: Long, percent: Int) = ScoreRecord(
        timestamp = ts,
        percent = percent,
        cleanHits = percent,
        totalNotes = 100,
        wrongPresses = 100 - percent,
        practiceMode = PracticeMode.BOTH,
    )

    @Test
    fun loadReturnsEmptyWhenNoFile() {
        val dir = tempDir()
        try {
            assertTrue(ScoreHistoryStorage.load("s1", dir).isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun appendPersistsAndReturnsNewestFirst() {
        val dir = tempDir()
        try {
            val older = record(1000L, 50)
            val newer = record(2000L, 80)
            ScoreHistoryStorage.append("s1", older, dir)
            val result = ScoreHistoryStorage.append("s1", newer, dir)
            assertEquals(listOf(newer, older), result)
            // 落盘后仍可读取
            assertEquals(listOf(newer, older), ScoreHistoryStorage.load("s1", dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun appendSortsByTimestampDescending() {
        val dir = tempDir()
        try {
            // 故意乱序插入
            ScoreHistoryStorage.append("s1", record(3000L, 70), dir)
            ScoreHistoryStorage.append("s1", record(1000L, 40), dir)
            ScoreHistoryStorage.append("s1", record(2000L, 60), dir)
            val loaded = ScoreHistoryStorage.load("s1", dir)
            assertEquals(listOf(3000L, 2000L, 1000L), loaded.map { it.timestamp })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun appendKeepsOnlyNewestFifty() {
        val dir = tempDir()
        try {
            for (i in 0 until 55) {
                ScoreHistoryStorage.append("s1", record(ts = 1000L + i, percent = i), dir)
            }
            val loaded = ScoreHistoryStorage.load("s1", dir)
            assertEquals(50, loaded.size)
            // 仅保留最新的 50 条（时间戳最大的 50 个）
            assertEquals(1000L + 54, loaded.first().timestamp)
            assertEquals(1000L + 5, loaded.last().timestamp)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun separateScoresKeptSeparately() {
        val dir = tempDir()
        try {
            ScoreHistoryStorage.append("a", record(1000L, 10), dir)
            ScoreHistoryStorage.append("b", record(2000L, 20), dir)
            ScoreHistoryStorage.append("a", record(3000L, 30), dir)
            assertEquals(2, ScoreHistoryStorage.load("a", dir).size)
            assertEquals(1, ScoreHistoryStorage.load("b", dir).size)
        } finally {
            dir.deleteRecursively()
        }
    }
}
