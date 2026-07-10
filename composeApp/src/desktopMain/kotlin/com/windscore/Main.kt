package com.windscore

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.WindowPlacement
import com.windscore.midi.createMidiManager

fun main() = application {
    val windowState = rememberWindowState(placement = WindowPlacement.Maximized)
    Window(
        onCloseRequest = ::exitApplication,
        title = "WindScore — 钢琴伴学",
        state = windowState,
    ) {
        App(createMidiManager(), isCompact = false)
    }
}
