package com.windscore.midi

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class MidiEventQueueTest {
    @Test
    fun preservesAllEventsBeyondPreviousSharedFlowCapacity() = runBlocking {
        val queue = MidiEventQueue()
        val expected = buildList {
            repeat(300) { index ->
                val pitch = 21 + index % 88
                add(MidiEvent.NoteOn(pitch, 100))
                add(MidiEvent.NoteOff(pitch))
            }
        }

        expected.forEach(queue::emit)

        assertEquals(expected, queue.events.take(expected.size).toList())
    }

    @Test
    fun clearDiscardsEventsFromStoppedConnection() = runBlocking {
        val queue = MidiEventQueue()
        queue.emit(MidiEvent.NoteOn(60, 100))
        queue.emit(MidiEvent.NoteOff(60))

        queue.clear()
        queue.emit(MidiEvent.NoteOn(62, 90))

        assertEquals(listOf(MidiEvent.NoteOn(62, 90)), queue.events.take(1).toList())
    }
}
