package com.windscore.core

import com.windscore.core.practice.PracticeAttempt
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import java.io.File

@Serializable
internal data class AttemptFileEnvelope(
    val schemaVersion: Int,
    val attempt: PracticeAttempt,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }
}

@OptIn(ExperimentalSerializationApi::class)
internal object AttemptStorage {

    private val cbor = Cbor

    private fun attemptsDir(baseDir: File) = File(baseDir, "attempts")
    private fun component(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9_-]{1,200}"))) { "Unsafe recording path component" }
        return value
    }
    private fun scoreDir(scoreId: String, baseDir: File) = File(attemptsDir(baseDir), component(scoreId))
    private fun attemptFile(scoreId: String, attemptId: String, baseDir: File) =
        File(scoreDir(scoreId, baseDir), "${component(attemptId)}.cbor")

    fun save(attempt: PracticeAttempt, baseDir: File = File(getAppStorageDir())) {
        val scoreId = attempt.context.scoreId
        if (scoreId.isEmpty()) return
        val bytes = cbor.encodeToByteArray(
            AttemptFileEnvelope.serializer(),
            AttemptFileEnvelope(AttemptFileEnvelope.CURRENT_SCHEMA_VERSION, attempt),
        )
        attemptFile(scoreId, attempt.attemptId, baseDir).atomicWriteBytes(bytes)
    }

    fun load(scoreId: String, baseDir: File = File(getAppStorageDir())): List<PracticeAttempt> {
        if (scoreId.isEmpty()) return emptyList()
        val dir = scoreDir(scoreId, baseDir)
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".cbor") } ?: return emptyList()
        return files
            .mapNotNull { file ->
                runCatching {
                    val envelope = cbor.decodeFromByteArray(AttemptFileEnvelope.serializer(), file.readBytes())
                    if (envelope.schemaVersion in 1..AttemptFileEnvelope.CURRENT_SCHEMA_VERSION) {
                        envelope.attempt.copy(recordingSchemaVersion = envelope.schemaVersion)
                    } else null
                }.getOrNull()
            }
            .sortedByDescending { it.context.startedAt }
    }

    fun cleanupTempFiles(baseDir: File = File(getAppStorageDir())): Int {
        val dir = attemptsDir(baseDir)
        if (!dir.exists()) return 0
        var removed = 0
        dir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".tmp") }
            .forEach { if (it.delete()) removed++ }
        return removed
    }
}
