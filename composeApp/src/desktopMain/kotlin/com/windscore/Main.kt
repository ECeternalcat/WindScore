package com.windscore

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.WindowPlacement
import com.windscore.midi.createMidiManager
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

fun main() = application {
    val windowState = rememberWindowState(placement = WindowPlacement.Maximized)

    var spaceCount by remember { mutableIntStateOf(0) }
    var darkTheme by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val mgr = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val dispatcher = KeyEventDispatcher { e ->
            if (e.id == KeyEvent.KEY_PRESSED && e.keyCode == KeyEvent.VK_SPACE) {
                spaceCount++
                true
            } else {
                false
            }
        }
        mgr.addKeyEventDispatcher(dispatcher)
        setKeepAwake(true)
        onDispose {
            mgr.removeKeyEventDispatcher(dispatcher)
            setKeepAwake(false)
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "WindScore",
        state = windowState,
    ) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            try {
                val screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize()
                window.minimumSize = java.awt.Dimension(screenSize.width, screenSize.height)
                window.extendedState = java.awt.Frame.MAXIMIZED_BOTH
                window.setLocation(0, 0)
            } catch (_: Throwable) {}
        }
        androidx.compose.runtime.LaunchedEffect(darkTheme) {
            com.windscore.ui.setWindowImmersiveDarkMode(window, darkTheme)
        }
        App(
            midiManager = createMidiManager(),
            isCompact = false,
            spacePressed = spaceCount,
            onDarkThemeChanged = { darkTheme = it },
        )
    }
}
