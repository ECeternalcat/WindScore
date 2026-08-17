package com.windscore.midi

import kotlin.test.Test
import kotlin.test.assertEquals

class MidiStreamParserTest {
    @Test
    fun parsesMessagesSplitAcrossCallbacks() {
        val events = mutableListOf<MidiEvent>()
        val parser = MidiStreamParser(events::add)

        parser.feed(byteArrayOf(0x90.toByte(), 60))
        parser.feed(byteArrayOf(100, 0x80.toByte(), 60))
        parser.feed(byteArrayOf(0))

        assertEquals(listOf(MidiEvent.NoteOn(60, 100), MidiEvent.NoteOff(60)), events)
    }

    @Test
    fun supportsRunningStatusAndZeroVelocityNoteOff() {
        val events = mutableListOf<MidiEvent>()
        val parser = MidiStreamParser(events::add)

        parser.feed(byteArrayOf(0x90.toByte(), 60, 100, 61, 0, 62, 90))

        assertEquals(
            listOf(MidiEvent.NoteOn(60, 100), MidiEvent.NoteOff(61), MidiEvent.NoteOn(62, 90)),
            events,
        )
    }

    @Test
    fun realtimeBytesDoNotInterruptChannelMessage() {
        val events = mutableListOf<MidiEvent>()
        val parser = MidiStreamParser(events::add)

        parser.feed(byteArrayOf(0x90.toByte(), 64, 0xF8.toByte(), 127))

        assertEquals(listOf<MidiEvent>(MidiEvent.NoteOn(64, 127)), events)
    }

    @Test
    fun systemMessagesClearRunningStatus() {
        val events = mutableListOf<MidiEvent>()
        val parser = MidiStreamParser(events::add)

        parser.feed(byteArrayOf(0x90.toByte(), 60, 100, 0xF1.toByte(), 1, 61, 100))

        assertEquals(listOf<MidiEvent>(MidiEvent.NoteOn(60, 100)), events)
    }
}
