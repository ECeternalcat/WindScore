package com.windscore.midi

import kotlin.test.Test
import kotlin.test.assertEquals

class UsbMidiEventDecoderTest {
    @Test
    fun emitsNoteEventsFromUsbPackets() {
        val events = mutableListOf<MidiEvent>()
        val decoder = UsbMidiEventDecoder(events::add)

        decoder.feed(
            byteArrayOf(
                0x09, 0x90.toByte(), 60, 100,
                0x08, 0x80.toByte(), 60, 0,
                0x19, 0x91.toByte(), 64, 0,
            ),
        )

        assertEquals(
            listOf(MidiEvent.NoteOn(60, 100), MidiEvent.NoteOff(60), MidiEvent.NoteOff(64)),
            events,
        )
    }

    @Test
    fun keepsSysExStateIsolatedBetweenVirtualCables() {
        val events = mutableListOf<MidiEvent>()
        val decoder = UsbMidiEventDecoder(events::add)

        decoder.feed(
            byteArrayOf(
                0x04, 0xF0.toByte(), 1, 2,
                0x19, 0x91.toByte(), 64, 100,
                0x07, 3, 4, 0xF7.toByte(),
            ),
        )

        assertEquals(listOf<MidiEvent>(MidiEvent.NoteOn(64, 100)), events)
    }
}
