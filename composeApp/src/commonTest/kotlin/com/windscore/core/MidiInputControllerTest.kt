package com.windscore.core

import com.windscore.audio.AudioSynth
import com.windscore.midi.MidiDiagnostic
import com.windscore.midi.MidiEvent
import com.windscore.midi.MidiManager
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

class MidiInputControllerTest {

    @Test
    fun firstConsumingHandlerStopsPropagation() {
        val controller = testController()
        val calls = mutableListOf<String>()
        controller.register(handler("scoring", calls, consume = true))
        controller.register(handler("practice", calls, consume = true))

        controller.dispatch(MidiEvent.NoteOn(60, 100))
        controller.dispatch(MidiEvent.NoteOff(60))

        assertEquals(listOf("scoring:on(60)", "scoring:off(60)"), calls)
    }

    @Test
    fun eventFallsThroughWhenHandlerDoesNotConsume() {
        val controller = testController()
        val calls = mutableListOf<String>()
        controller.register(handler("scoring", calls, consume = false))
        controller.register(handler("practice", calls, consume = true))

        controller.dispatch(MidiEvent.NoteOn(60, 100))
        controller.dispatch(MidiEvent.NoteOff(60))

        assertEquals(
            listOf("scoring:on(60)", "practice:on(60)", "scoring:off(60)", "practice:off(60)"),
            calls,
        )
    }

    @Test
    fun synthPassthroughFiresForEveryEvent() {
        val synth = RecordingSynth()
        val manager = FakeMidiManager()
        val controller = MidiInputController(manager, synth, testScope())
        controller.register(handler("h", mutableListOf(), consume = true))

        controller.dispatch(MidiEvent.NoteOn(64, 90))
        controller.dispatch(MidiEvent.NoteOff(64))

        assertEquals(listOf("on:0/64/90", "off:0/64"), synth.calls)
    }

    @Test
    fun runningLifecycleIsIdempotentAndStopsManager() = runBlocking {
        val manager = FakeMidiManager()
        val controller = MidiInputController(manager, RecordingSynth(), testScope())

        controller.setRunning(true)
        controller.setRunning(true)
        assertEquals(1, manager.started)
        assertTrue(controller.running.value)

        controller.setRunning(false)
        controller.setRunning(false)
        assertEquals(1, manager.stopped)
        assertEquals(false, controller.running.value)
    }

    @Test
    fun runningCollectsManagerEventsUntilStopped() = runBlocking {
        val manager = FakeMidiManager()
        val controller = MidiInputController(manager, RecordingSynth(), testScope())
        val calls = mutableListOf<String>()
        controller.register(handler("practice", calls, consume = true))

        controller.setRunning(true)
        manager.emit(MidiEvent.NoteOn(60, 100))
        eventually { calls.size == 1 }

        controller.setRunning(false)
        manager.emit(MidiEvent.NoteOn(62, 100))
        delay(100)
        assertEquals(listOf("practice:on(60)"), calls)
    }

    @Test
    fun managerDiagnosticsMapToAppDiagnostics() = runBlocking {
        val manager = FakeMidiManager()
        val diagnostics = DiagnosticController()
        val received = mutableListOf<AppDiagnostic>()
        val collector = launch { diagnostics.events.collect { received += it } }
        val controller = MidiInputController(manager, RecordingSynth(), testScope(), diagnostics)

        manager.emitDiagnostic(MidiDiagnostic.DeviceOpenFailed(cause = null))
        eventually { received.size == 1 }
        assertEquals(
            MidiDiagnosticProblem.DEVICE_OPEN_FAILED,
            (received.single() as AppDiagnostic.Midi).problem,
        )
        collector.cancel()
    }

    private fun testScope() = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private fun testController() = MidiInputController(FakeMidiManager(), RecordingSynth(), testScope())

    private fun handler(name: String, calls: MutableList<String>, consume: Boolean) =
        object : MidiNoteHandler {
            override fun onNoteOn(pitch: Int): Boolean {
                calls += "$name:on($pitch)"
                return consume
            }

            override fun onNoteOff(pitch: Int): Boolean {
                calls += "$name:off($pitch)"
                return consume
            }
        }

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

        var started = 0
            private set
        var stopped = 0
            private set

        override fun start() {
            started++
        }

        override fun stop() {
            stopped++
        }

        fun emit(event: MidiEvent) {
            _events.tryEmit(event)
        }

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
