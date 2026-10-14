package com.windscore.core

import com.windscore.audio.AudioSynth
import com.windscore.midi.MidiDiagnostic
import com.windscore.midi.MidiEvent
import com.windscore.midi.MidiManager
import com.windscore.midi.MidiRecordingSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MidiInputControllerSinkTest {

    @Test
    fun sinkReceivesEventsBeforeHandlersWithoutConsuming() {
        val scope = testScope()
        val controller = MidiInputController(FakeMidiManager(), RecordingSynth(), scope)
        try {
            val calls = mutableListOf<String>()
            controller.register(
                object : MidiNoteHandler {
                    override fun onNoteOn(pitch: Int): Boolean {
                        calls += "handler:on($pitch)"
                        return true
                    }

                    override fun onNoteOff(pitch: Int): Boolean {
                        calls += "handler:off($pitch)"
                        return true
                    }
                },
            )
            controller.recordingSink = recordingSink(calls)

            controller.dispatch(MidiEvent.NoteOn(60, 100))
            controller.dispatch(MidiEvent.NoteOff(60))

            assertEquals(
                listOf("sink:on(60)", "handler:on(60)", "sink:off(60)", "handler:off(60)"),
                calls,
            )
        } finally {
            controller.close()
            scope.cancel()
        }
    }

    @Test
    fun sinkFailureDoesNotBreakDispatch() {
        val scope = testScope()
        val synth = RecordingSynth()
        val controller = MidiInputController(FakeMidiManager(), synth, scope)
        try {
            val calls = mutableListOf<String>()
            controller.register(
                object : MidiNoteHandler {
                    override fun onNoteOn(pitch: Int): Boolean {
                        calls += "handler:on($pitch)"
                        return true
                    }

                    override fun onNoteOff(pitch: Int): Boolean {
                        calls += "handler:off($pitch)"
                        return true
                    }
                },
            )
            controller.recordingSink = object : MidiRecordingSink {
                override fun onMidiEvent(event: MidiEvent) = throw IllegalStateException("sink boom")
                override fun onMidiDisconnected() = throw IllegalStateException("sink boom")
            }

            controller.dispatch(MidiEvent.NoteOn(60, 100))
            controller.dispatch(MidiEvent.NoteOff(60))

            assertEquals(listOf("handler:on(60)", "handler:off(60)"), calls)
            assertEquals(listOf("on:0/60/100", "off:0/60"), synth.calls)
        } finally {
            controller.close()
            scope.cancel()
        }
    }

    @Test
    fun disconnectDiagnosticNotifiesSink() = runBlocking {
        val scope = testScope()
        val manager = FakeMidiManager()
        val controller = MidiInputController(manager, RecordingSynth(), scope)
        val notifications = mutableListOf<Unit>()
        controller.recordingSink = object : MidiRecordingSink {
            override fun onMidiEvent(event: MidiEvent) = Unit
            override fun onMidiDisconnected() {
                notifications += Unit
            }
        }
        try {
            manager.emitDiagnostic(MidiDiagnostic.Disconnected)
            eventually { notifications.size == 1 }
            assertEquals(1, notifications.size)

            manager.emitDiagnostic(MidiDiagnostic.NoInputDevice)
            delay(100)
            assertEquals(1, notifications.size)
        } finally {
            controller.close()
            scope.cancel()
        }
    }

    private fun recordingSink(calls: MutableList<String>) = object : MidiRecordingSink {
        override fun onMidiEvent(event: MidiEvent) {
            calls += when (event) {
                is MidiEvent.NoteOn -> "sink:on(${event.pitch})"
                is MidiEvent.NoteOff -> "sink:off(${event.pitch})"
            }
        }

        override fun onMidiDisconnected() {
            calls += "sink:disconnected"
        }
    }

    private fun testScope() = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private suspend fun eventually(predicate: () -> Boolean) {
        repeat(100) {
            if (predicate()) return
            delay(10)
        }
        assertTrue(predicate(), "condition was not reached")
    }

    private class FakeMidiManager : MidiManager {
        private val _events = MutableSharedFlow<MidiEvent>(replay = 1)
        override val events: Flow<MidiEvent> = _events

        private val _diagnostics = MutableSharedFlow<MidiDiagnostic>(replay = 1)
        override val diagnostics: Flow<MidiDiagnostic> = _diagnostics

        override fun start() = Unit

        override fun stop() = Unit

        fun emitDiagnostic(diagnostic: MidiDiagnostic) {
            _diagnostics.tryEmit(diagnostic)
        }
    }

    private class RecordingSynth : AudioSynth {
        val calls = mutableListOf<String>()
        override fun start() = Unit
        override fun noteOn(channel: Int, pitch: Int, velocity: Int) {
            calls += "on:$channel/$pitch/$velocity"
        }
        override fun noteOff(channel: Int, pitch: Int) {
            calls += "off:$channel/$pitch"
        }
        override fun stop() = Unit
    }
}
