package com.windscore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.windscore.audio.createAudioSynth
import com.windscore.core.engine.ScoreSession
import com.windscore.core.loadScore
import com.windscore.core.model.PracticeMode
import com.windscore.core.model.Score
import com.windscore.midi.MidiEvent
import com.windscore.midi.MidiManager
import com.windscore.ui.PianoRoll
import com.windscore.ui.ResultScreen
import com.windscore.ui.ScoreFilePickerButton
import com.windscore.ui.ScoreSheetView

@Composable
fun App(midiManager: MidiManager) {
    var running by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf<Score?>(null) }
    var session by remember { mutableStateOf<ScoreSession?>(null) }
    var practiceMode by remember { mutableStateOf(PracticeMode.BOTH) }
    var showSheet by remember { mutableStateOf(false) }
    val synth = remember { createAudioSynth() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(running) {
        if (running) {
            synth.start()
            midiManager.start()
            midiManager.events.collect { ev ->
                when (ev) {
                    is MidiEvent.NoteOn -> {
                        synth.noteOn(ev.pitch, ev.velocity)
                        session?.onNoteOn(ev.pitch)
                    }
                    is MidiEvent.NoteOff -> {
                        synth.noteOff(ev.pitch)
                        session?.onNoteOff(ev.pitch)
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            midiManager.stop()
            synth.stop()
        }
    }

    fun startSession(loaded: Score, mode: PracticeMode) {
        score = loaded
        practiceMode = mode
        session = ScoreSession(loaded, synth, scope, mode)
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(8.dp)) {
                Text("WindScore — 钢琴伴学", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Button(onClick = { startSession(loadScore(), practiceMode) }) {
                        Text("解析乐谱")
                    }
                    Spacer(Modifier.width(8.dp))
                    ScoreFilePickerButton(onPicked = { startSession(it, practiceMode) })
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = session != null, onClick = { session?.reset() }) {
                        Text("重置")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        running = !running
                        if (!running) {
                            midiManager.stop()
                            synth.stop()
                        }
                    }) {
                        Text(if (running) "停止监听 MIDI" else "开始监听 MIDI")
                    }
                }

                Spacer(Modifier.height(4.dp))
                // 练习模式选择（文档 4.2 单/双手分离）
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PracticeModeChip("双手", PracticeMode.BOTH, practiceMode, session != null) {
                        if (session != null) startSession(score!!, it) else practiceMode = it
                    }
                    PracticeModeChip("只练右手", PracticeMode.RIGHT_ONLY, practiceMode, session != null) {
                        if (session != null) startSession(score!!, it) else practiceMode = it
                    }
                    PracticeModeChip("只练左手", PracticeMode.LEFT_ONLY, practiceMode, session != null) {
                        if (session != null) startSession(score!!, it) else practiceMode = it
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = !showSheet,
                        onClick = { showSheet = false },
                        enabled = session != null,
                        label = { Text("卷帘") },
                    )
                    FilterChip(
                        selected = showSheet,
                        onClick = { showSheet = true },
                        enabled = session != null,
                        label = { Text("看谱(OSMD)") },
                    )
                }

                val s = session
                if (s != null) {
                    Spacer(Modifier.height(4.dp))
                    val pending = s.pendingPitches.joinToString { pitchName(it) }
                    val held = s.activeKeys.sorted().joinToString { pitchName(it) }
                    Text(
                        "进度 ${s.progress}/${s.groupCount}  tick=${s.currentTick}/${s.score.totalTicks}  " +
                            if (s.finished) "完成！" else "该弹: $pending",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (s.finished) Color(0xFF4CFF7A) else Color.Unspecified,
                    )
                    Text("当前按下: $held", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxSize()) {
                        if (showSheet && s.score.rawXml != null) {
                            ScoreSheetView(
                                rawXml = s.score.rawXml,
                                currentTickProvider = { s.currentTick },
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            PianoRoll(
                                score = s.score,
                                currentTickProvider = { s.currentTick },
                                modifier = Modifier.fillMaxSize(),
                                hostedTrack = s.hostedTrack,
                                heldPitchesProvider = { s.heldPitches },
                            )
                        }
                        if (s.finished) {
                            ResultScreen(
                                session = s,
                                modifier = Modifier.fillMaxSize(),
                                onReset = { s.reset() },
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxSize()) {
                        Text(
                            "点击“解析乐谱”，再“开始监听 MIDI”。\n" +
                                "等待模式：按对当前高亮音符（白边）才会继续掉落，按错不惩罚。\n" +
                                "可切换“只练右手/左手”：另一只手由系统自动托管发声。",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeModeChip(
    label: String,
    mode: PracticeMode,
    current: PracticeMode,
    enabled: Boolean,
    onSelect: (PracticeMode) -> Unit,
) {
    FilterChip(
        selected = current == mode,
        onClick = { onSelect(mode) },
        enabled = enabled,
        label = { Text(label) },
    )
}

private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

fun pitchName(pitch: Int): String {
    val name = NOTE_NAMES[pitch.mod(12)]
    val octave = pitch / 12 - 1
    return "$name$octave"
}
