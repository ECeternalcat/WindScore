package com.windscore.midi

/** Converts USB-MIDI 1.0 packets to events while keeping each virtual cable's MIDI state isolated. */
internal class UsbMidiEventDecoder(
    emitEvent: (MidiEvent) -> Unit,
) {
    private val cableParsers = mutableMapOf<Int, MidiStreamParser>()
    private val packetDecoder = UsbMidiPacketDecoder { cable, bytes, offset, count ->
        cableParsers.getOrPut(cable) { MidiStreamParser(emitEvent) }.feed(bytes, offset, count)
    }

    fun feed(bytes: ByteArray, offset: Int = 0, count: Int = bytes.size - offset) {
        packetDecoder.feed(bytes, offset, count)
    }
}
