package com.windscore.midi

interface MidiRecordingSink {
    fun onMidiEvent(event: MidiEvent)

    fun onMidiDisconnected()

    fun onMidiEnabledChanged(enabled: Boolean) {}
}
