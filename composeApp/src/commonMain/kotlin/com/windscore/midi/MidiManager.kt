package com.windscore.midi

import kotlinx.coroutines.flow.Flow

interface MidiManager {
    val events: Flow<MidiEvent>
    fun start()
    fun stop()
}
