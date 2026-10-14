package com.windscore.core

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

enum class LibraryDiagnosticOperation { LOAD, OPEN, HISTORY, SAVE }

enum class SettingsDiagnosticOperation { LOAD, SAVE }

enum class MidiDiagnosticProblem {
    UNSUPPORTED,
    NO_INPUT_DEVICE,
    DEVICE_OPEN_FAILED,
    DISCONNECTED,
    RUNTIME_FAILED,
}

enum class RecordingDiagnosticOperation { SAVE, LOAD, CLEANUP }

sealed interface AppDiagnostic {
    data class Import(val cause: Throwable) : AppDiagnostic

    data class Library(
        val operation: LibraryDiagnosticOperation,
        val cause: Throwable,
    ) : AppDiagnostic

    data class Settings(
        val operation: SettingsDiagnosticOperation,
        val cause: Throwable,
    ) : AppDiagnostic

    data class Midi(
        val problem: MidiDiagnosticProblem,
        val cause: Throwable? = null,
    ) : AppDiagnostic

    data class Recording(
        val operation: RecordingDiagnosticOperation,
        val cause: Throwable,
    ) : AppDiagnostic
}

/** Buffered, single-consumer diagnostics for transient recoverable failures. */
class DiagnosticController {
    private val channel = Channel<AppDiagnostic>(Channel.BUFFERED)

    val events: Flow<AppDiagnostic> = channel.receiveAsFlow()

    fun report(diagnostic: AppDiagnostic) {
        channel.trySend(diagnostic)
    }
}
