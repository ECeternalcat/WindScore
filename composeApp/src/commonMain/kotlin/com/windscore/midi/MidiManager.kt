package com.windscore.midi

import kotlinx.coroutines.flow.Flow

sealed interface MidiDiagnostic {
    data object Unsupported : MidiDiagnostic
    data object NoInputDevice : MidiDiagnostic
    data class DeviceOpenFailed(val cause: Throwable? = null) : MidiDiagnostic
    data object Disconnected : MidiDiagnostic
}

interface MidiManager {
    val events: Flow<MidiEvent>
    val diagnostics: Flow<MidiDiagnostic>
    fun start()
    fun stop()
}
