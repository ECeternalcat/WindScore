package com.windscore.midi

import kotlin.test.Test
import kotlin.test.assertContentEquals

class UsbMidiPacketDecoderTest {
    @Test
    fun decodesChannelMessagesAcrossUsbReadBoundaries() {
        val midiBytes = mutableListOf<Byte>()
        val decoder = UsbMidiPacketDecoder { _, bytes, offset, count ->
            midiBytes += bytes.copyOfRange(offset, offset + count).toList()
        }

        decoder.feed(byteArrayOf(0x09, 0x90.toByte()))
        decoder.feed(byteArrayOf(60, 100, 0x08, 0x80.toByte(), 60))
        decoder.feed(byteArrayOf(0))

        assertContentEquals(
            byteArrayOf(0x90.toByte(), 60, 100, 0x80.toByte(), 60, 0),
            midiBytes.toByteArray(),
        )
    }

    @Test
    fun decodesPacketsFromAnyCableAndDropsReservedCin() {
        val midiBytes = mutableListOf<Byte>()
        val cables = mutableListOf<Int>()
        val decoder = UsbMidiPacketDecoder { cable, bytes, offset, count ->
            cables += cable
            midiBytes += bytes.copyOfRange(offset, offset + count).toList()
        }

        decoder.feed(
            byteArrayOf(
                0x19, 0x91.toByte(), 64, 127,
                0x00, 0x90.toByte(), 65, 100,
                0x2C, 0xC2.toByte(), 10, 0,
            ),
        )

        assertContentEquals(
            byteArrayOf(0x91.toByte(), 64, 127, 0xC2.toByte(), 10),
            midiBytes.toByteArray(),
        )
        assertContentEquals(listOf(1, 2), cables)
    }
}
