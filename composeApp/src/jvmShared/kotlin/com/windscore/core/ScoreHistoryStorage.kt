package com.windscore.core

import com.windscore.core.model.ScoreRecord
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 评分历史的持久化（Desktop / Android 共享）。
 * 每个乐谱一个文件 `<appStorageDir>/history/<scoreId>.json`，按时间倒序（最新在前），最多保留 [MAX_RECORDS] 条。
 */
internal object ScoreHistoryStorage {
    private const val MAX_RECORDS = 50
    private fun dir(baseDir: File) = File(baseDir, "history")
    private fun file(scoreId: String, baseDir: File) = File(dir(baseDir), "$scoreId.json")

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    private val listSerializer = ListSerializer(ScoreRecord.serializer())

    fun load(scoreId: String, baseDir: File = File(getAppStorageDir())): List<ScoreRecord> {
        if (scoreId.isEmpty()) return emptyList()
        val f = file(scoreId, baseDir)
        if (!f.exists()) return emptyList()
        return runCatching {
            json.decodeFromString(listSerializer, f.readText(Charsets.UTF_8))
        }.getOrElse {
            println("[ScoreHistory] Failed to load history for $scoreId: ${it.message}")
            emptyList()
        }
    }

    /** 追加一条记录（最新在前），落盘并返回更新后的列表。 */
    fun append(scoreId: String, record: ScoreRecord, baseDir: File = File(getAppStorageDir())): List<ScoreRecord> {
        if (scoreId.isEmpty()) return emptyList()
        val updated = (load(scoreId, baseDir) + record)
            .sortedByDescending { it.timestamp }
            .take(MAX_RECORDS)
        runCatching {
            if (!dir(baseDir).exists()) dir(baseDir).mkdirs()
            file(scoreId, baseDir).atomicWriteText(json.encodeToString(listSerializer, updated))
        }.onFailure { println("[ScoreHistory] Failed to save history for $scoreId: ${it.message}") }
        return updated
    }
}
