package com.windscore.core

import com.windscore.audio.AudioSynth
import com.windscore.midi.MidiDiagnostic
import com.windscore.midi.MidiEvent
import com.windscore.midi.MidiManager
import com.windscore.midi.MidiRecordingSink
import com.windscore.core.traceState
import com.windscore.core.engine.SessionLane
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MIDI input event arbitration.
 *
 * Handlers are consulted in registration order; the first handler that
 * consumes an event stops propagation. Register higher-priority consumers
 * first (e.g. scoring before practice). New practice models plug in by
 * registering additional handlers instead of extending call-site branches.
 */
interface MidiNoteHandler {
    /** Returns true if the event was consumed and must not propagate further. */
    fun onNoteOn(pitch: Int): Boolean

    /** Returns true if the event was consumed and must not propagate further. */
    fun onNoteOff(pitch: Int): Boolean
}

/**
 * Owns the MIDI input lifecycle and routes incoming events to the synth
 * (passthrough feedback) and registered [MidiNoteHandler]s. Replaces the
 * inline arbitration that used to live in `App.kt`.
 */
class MidiInputController(
    private val midiManager: MidiManager,
    private val synth: AudioSynth,
    private val scope: CoroutineScope,
    private val diagnostics: DiagnosticController? = null,
) {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _handlers = MutableStateFlow<List<MidiNoteHandler>>(emptyList())

    private val handlers: List<MidiNoteHandler> get() = _handlers.value

    private val _recordingSink = MutableStateFlow<MidiRecordingSink?>(null)

    /** Non-consuming recording side channel: sees every dispatched event before synth/handlers. */
    var recordingSink: MidiRecordingSink?
        get() = _recordingSink.value
        set(value) {
            _recordingSink.value = value
        }

    private var collectJob: Job? = null
    private var diagnosticJob: Job? = null
    private var generation = 0L

    init {
        diagnosticJob = scope.launch {
            midiManager.diagnostics.collect { diagnostic ->
                val problem = when (diagnostic) {
                    MidiDiagnostic.Unsupported -> MidiDiagnosticProblem.UNSUPPORTED
                    MidiDiagnostic.NoInputDevice -> MidiDiagnosticProblem.NO_INPUT_DEVICE
                    is MidiDiagnostic.DeviceOpenFailed -> MidiDiagnosticProblem.DEVICE_OPEN_FAILED
                    MidiDiagnostic.Disconnected -> MidiDiagnosticProblem.DISCONNECTED
                }
                val cause = (diagnostic as? MidiDiagnostic.DeviceOpenFailed)?.cause
                diagnostics?.report(AppDiagnostic.Midi(problem, cause))
                SessionLane.run {
                    if (diagnostic == MidiDiagnostic.Disconnected) {
                        runCatching { _recordingSink.value?.onMidiDisconnected() }
                        stop()
                    } else if (diagnostic == MidiDiagnostic.Unsupported || diagnostic == MidiDiagnostic.NoInputDevice) {
                        stop()
                    }
                }
            }
        }
    }

    fun register(handler: MidiNoteHandler) {
        _handlers.update { if (it.contains(handler)) it else it + handler }
    }

    fun unregister(handler: MidiNoteHandler) {
        _handlers.update { it - handler }
    }

    fun toggle() = setRunning(!_running.value)

    fun setRunning(enabled: Boolean) = SessionLane.run {
        if (enabled) start() else stop()
    }

    fun close() {
        stop()
        diagnosticJob?.cancel()
        diagnosticJob = null
    }

    internal fun dispatch(event: MidiEvent) = SessionLane.run {
        traceState("midi.dispatch event=$event")
        _recordingSink.value?.let { sink ->
            runCatching { sink.onMidiEvent(event) }
        }
        when (event) {
            is MidiEvent.NoteOn -> {
                synth.noteOn(event.pitch, event.velocity)
                for (handler in handlers) if (handler.onNoteOn(event.pitch)) break
            }
            is MidiEvent.NoteOff -> {
                synth.noteOff(event.pitch)
                for (handler in handlers) if (handler.onNoteOff(event.pitch)) break
            }
        }
    }

    private fun start() {
        if (_running.value) return
        _running.value = true
        val currentGeneration = ++generation
        try {
            synth.start()
            midiManager.start()
            runCatching { _recordingSink.value?.onMidiEnabledChanged(true) }
        } catch (error: Exception) {
            diagnostics?.report(AppDiagnostic.Midi(MidiDiagnosticProblem.RUNTIME_FAILED, error))
            stop()
            return
        }
        collectJob = scope.launch {
            try {
                midiManager.events.collect { event ->
                    SessionLane.run {
                        if (_running.value && generation == currentGeneration) dispatch(event)
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                diagnostics?.report(AppDiagnostic.Midi(MidiDiagnosticProblem.RUNTIME_FAILED, error))
                SessionLane.run {
                    runCatching { _recordingSink.value?.onMidiDisconnected() }
                    stop()
                }
            }
        }
    }

    private fun stop() {
        if (!_running.value) return
        _running.value = false
        generation++
        runCatching { _recordingSink.value?.onMidiEnabledChanged(false) }
        collectJob?.cancel()
        collectJob = null
        midiManager.stop()
    }
}
