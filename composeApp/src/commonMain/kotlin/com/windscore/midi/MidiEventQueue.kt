package com.windscore.midi

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import com.windscore.core.traceState

/** Single-consumer queue. MIDI NoteOff events must never be dropped under temporary UI backpressure. */
internal class MidiEventQueue {
    private val channel = Channel<MidiEvent>(Channel.UNLIMITED)

    val events: Flow<MidiEvent> = channel.receiveAsFlow()

    fun emit(event: MidiEvent) {
        traceState("midi.queue enqueue event=$event")
        check(channel.trySend(event).isSuccess) { "MIDI event queue is unavailable" }
    }

    fun clear() {
        while (channel.tryReceive().isSuccess) {
            // Discard input from the previous stopped connection.
        }
    }
}
