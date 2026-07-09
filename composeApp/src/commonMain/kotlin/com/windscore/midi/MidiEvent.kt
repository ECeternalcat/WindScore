package com.windscore.midi

sealed interface MidiEvent {
    data class NoteOn(val pitch: Int, val velocity: Int) : MidiEvent
    data class NoteOff(val pitch: Int) : MidiEvent
}
