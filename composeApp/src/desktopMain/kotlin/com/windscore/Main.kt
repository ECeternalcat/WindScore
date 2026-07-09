package com.windscore

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.windscore.midi.createMidiManager

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "WindScore") {
        App(createMidiManager())
    }
}
