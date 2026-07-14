package com.windscore

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
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
        // 保持屏幕常亮：每 30 秒模拟一次无害按键（F15），重置 OS 空闲计时器。
        // 使用 F15 而非 Shift，避免在用户于其他应用输入文本时干扰输入（Shift 会修饰按键）。
        val keepAwake = java.util.Timer("keepAwake", true)
        keepAwake.scheduleAtFixedRate(object : java.util.TimerTask() {
            override fun run() {
                try {
                    val robot = java.awt.Robot()
                    robot.keyPress(KeyEvent.VK_F15)
                    robot.keyRelease(KeyEvent.VK_F15)
                } catch (_: Exception) {}
            }
        }, 30000, 30000)
        onDispose {
            mgr.removeKeyEventDispatcher(dispatcher)
            keepAwake.cancel()
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
        androidx.compose.runtime.LaunchedEffect(com.windscore.ui.theme.isDarkThemeGlobal) {
            com.windscore.ui.setWindowImmersiveDarkMode(window, com.windscore.ui.theme.isDarkThemeGlobal)
        }
        App(createMidiManager(), isCompact = false, spacePressed = spaceCount)
    }
}
