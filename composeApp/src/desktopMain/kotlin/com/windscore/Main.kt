package com.windscore

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        // 保持屏幕常亮：每 30 秒模拟一次无害按键（Shift），重置 OS 空闲计时器
        val keepAwake = java.util.Timer("keepAwake", true)
        keepAwake.scheduleAtFixedRate(object : java.util.TimerTask() {
            override fun run() {
                try {
                    val robot = java.awt.Robot()
                    robot.keyPress(KeyEvent.VK_SHIFT)
                    robot.keyRelease(KeyEvent.VK_SHIFT)
                } catch (_: Exception) {}
            }
        }, 0, 30000)
        onDispose {
            mgr.removeKeyEventDispatcher(dispatcher)
            keepAwake.cancel()
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "WindScore — 钢琴伴学",
        state = windowState,
    ) {
        App(createMidiManager(), isCompact = false, spacePressed = spaceCount)
    }
}
