package com.windscore.midi

/** Stateful parser for a single MIDI byte stream. */
internal class MidiStreamParser(
    private val emit: (MidiEvent) -> Unit,
) {
    private var runningStatus: Int? = null
    private var messageStatus: Int? = null
    private var expectedDataBytes = 0
    private val dataBytes = IntArray(2)
    private var dataCount = 0
    private var inSysEx = false

    fun feed(bytes: ByteArray, offset: Int = 0, count: Int = bytes.size - offset) {
        require(offset >= 0 && count >= 0 && offset + count <= bytes.size)
        for (index in offset until offset + count) {
            accept(bytes[index].toInt() and 0xFF)
        }
    }

    private fun accept(value: Int) {
        if (value >= 0xF8) return // Realtime messages may appear inside any message.

        if (value and 0x80 != 0) {
            when {
                value == 0xF0 -> {
                    inSysEx = true
                    runningStatus = null
                    clearMessage()
                }
                value == 0xF7 -> {
                    inSysEx = false
                    clearMessage()
                }
                value < 0xF0 -> {
                    inSysEx = false
                    runningStatus = value
                    beginMessage(value)
                }
                else -> {
                    inSysEx = false
                    runningStatus = null
                    val length = systemDataLength(value)
                    if (length > 0) beginMessage(value, length) else clearMessage()
                }
            }
            return
        }

        if (inSysEx) return
        if (messageStatus == null) {
            val status = runningStatus ?: return
            beginMessage(status)
        }

        if (dataCount < dataBytes.size) dataBytes[dataCount] = value
        dataCount++
        if (dataCount >= expectedDataBytes) {
            emitMessage()
            clearMessage()
        }
    }

    private fun beginMessage(status: Int, length: Int = channelDataLength(status)) {
        messageStatus = status
        expectedDataBytes = length
        dataCount = 0
    }

    private fun emitMessage() {
        when (messageStatus?.and(0xF0)) {
            0x80 -> emit(MidiEvent.NoteOff(dataBytes[0]))
            0x90 -> {
                val pitch = dataBytes[0]
                val velocity = dataBytes[1]
                emit(if (velocity == 0) MidiEvent.NoteOff(pitch) else MidiEvent.NoteOn(pitch, velocity))
            }
        }
    }

    private fun clearMessage() {
        messageStatus = null
        expectedDataBytes = 0
        dataCount = 0
    }

    private fun channelDataLength(status: Int): Int =
        if (status and 0xF0 == 0xC0 || status and 0xF0 == 0xD0) 1 else 2

    private fun systemDataLength(status: Int): Int = when (status) {
        0xF1, 0xF3 -> 1
        0xF2 -> 2
        else -> 0
    }
}
