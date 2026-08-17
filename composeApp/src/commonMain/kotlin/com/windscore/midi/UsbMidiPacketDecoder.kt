package com.windscore.midi

/** Decodes USB-MIDI 1.0 four-byte event packets into an ordinary MIDI byte stream. */
internal class UsbMidiPacketDecoder(
    private val emit: (cable: Int, bytes: ByteArray, offset: Int, count: Int) -> Unit,
) {
    private val packet = ByteArray(USB_EVENT_PACKET_SIZE)
    private var packetSize = 0

    fun feed(bytes: ByteArray, offset: Int = 0, count: Int = bytes.size - offset) {
        require(offset >= 0 && count >= 0 && offset + count <= bytes.size)
        for (index in offset until offset + count) {
            packet[packetSize++] = bytes[index]
            if (packetSize == USB_EVENT_PACKET_SIZE) {
                val midiLength = midiLength(packet[0].toInt() and 0x0F)
                if (midiLength > 0) emit((packet[0].toInt() ushr 4) and 0x0F, packet, 1, midiLength)
                packetSize = 0
            }
        }
    }

    private fun midiLength(codeIndexNumber: Int): Int = when (codeIndexNumber) {
        0x2, 0x6, 0xC, 0xD -> 2
        0x3, 0x4, 0x7, 0x8, 0x9, 0xA, 0xB, 0xE -> 3
        0x5, 0xF -> 1
        else -> 0
    }

    private companion object {
        const val USB_EVENT_PACKET_SIZE = 4
    }
}
