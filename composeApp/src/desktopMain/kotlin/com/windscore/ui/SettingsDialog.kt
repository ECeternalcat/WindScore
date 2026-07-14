package com.windscore.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.windscore.ui.theme.WindScoreColors
import com.windscore.ui.theme.WindScoreTheme

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Memory
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.win32.StdCallLibrary

interface DwmApi : StdCallLibrary {
    fun DwmSetWindowAttribute(
        hwnd: HWND,
        dwAttribute: Int,
        pvAttribute: Pointer,
        cbAttribute: Int
    ): Int

    companion object {
        val INSTANCE = try {
            Native.load("dwmapi", DwmApi::class.java) as DwmApi
        } catch (_: Throwable) {
            null
        }
    }
}

fun setWindowImmersiveDarkMode(window: java.awt.Window, dark: Boolean) {
    if (!System.getProperty("os.name").lowercase().contains("win")) return
    val dwm = DwmApi.INSTANCE ?: return
    try {
        val hwnd = HWND(Native.getWindowPointer(window))
        val dwAttribute = 20 // DWMWA_USE_IMMERSIVE_DARK_MODE
        val pvAttribute = Memory(4).apply { setInt(0, if (dark) 1 else 0) }
        dwm.DwmSetWindowAttribute(hwnd, dwAttribute, pvAttribute, 4)
    } catch (_: Throwable) {}
}

@Composable
actual fun SettingsDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    if (visible) {
        DialogWindow(
            onCloseRequest = onDismiss,
            state = rememberDialogState(size = DpSize(480.dp, 560.dp)),
            title = "Settings / 设置",
            resizable = false,
        ) {
            androidx.compose.runtime.LaunchedEffect(com.windscore.ui.theme.isDarkThemeGlobal) {
                setWindowImmersiveDarkMode(window, com.windscore.ui.theme.isDarkThemeGlobal)
            }
            WindScoreTheme {
                Surface(
                    color = WindScoreColors.canvas
                ) {
                    content()
                }
            }
        }
    }
}
