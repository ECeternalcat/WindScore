package com.windscore.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color as FxColor
import javafx.scene.web.WebView
import javax.swing.JPanel

/**
 * Desktop 看谱模式：JavaFX WebView 嵌入 Swing（JFXPanel）→ SwingPanel 嵌入 Compose。
 * 加载 OSMD 渲染原始 MusicXML，并随 currentTick 推进驱动光标步进/自动翻页。
 * JavaFX 不可用时优雅降级为提示文字，绝不崩溃。
 */
@Composable
actual fun ScoreSheetView(
    rawXml: String,
    currentTickProvider: () -> Long,
    modifier: Modifier,
) {
    var state by remember { mutableStateOf<LoadState>(LoadState.Loading) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var lastTick by remember { mutableLongStateOf(0L) }

    SwingPanel(
        background = Color.White,
        factory = {
            try {
                try { Platform.startup {} } catch (_: IllegalStateException) { /* 已启动 */ }
                val panel = JFXPanel()
                Platform.runLater {
                    runCatching {
                        val webView = WebView()
                        webView.isContextMenuEnabled = false
                        webView.engine.isJavaScriptEnabled = true
                        panel.scene = Scene(StackPane(webView), FxColor.WHITE)
                        webView.engine.loadContent(OsmdHtml.build(rawXml))
                        webViewRef = webView
                        state = LoadState.Ok
                    }.onFailure {
                        println("[OSMD] WebView 初始化失败: ${it.message}")
                        state = LoadState.Failed
                    }
                }
                panel
            } catch (t: Throwable) {
                println("[OSMD] JavaFX 不可用: ${t.message}")
                state = LoadState.Failed
                JPanel()
            }
        },
        update = {},
        modifier = modifier,
    )

    // 切换乐谱时重新加载
    LaunchedEffect(rawXml) {
        val wv = webViewRef ?: return@LaunchedEffect
        Platform.runLater {
            runCatching { wv.engine.loadContent(OsmdHtml.build(rawXml)) }
        }
    }

    // currentTick 推进 → 光标步进
    val tick = currentTickProvider()
    LaunchedEffect(tick) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (tick > lastTick) {
            Platform.runLater {
                runCatching { wv.engine.executeScript("window.__advanceCursor && window.__advanceCursor(1);") }
            }
        }
        lastTick = tick
    }

    DisposableEffect(Unit) {
        onDispose {
            val wv = webViewRef
            if (wv != null) Platform.runLater { runCatching { wv.engine.loadContent(null) } }
        }
    }

    if (state == LoadState.Failed) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "看谱模式不可用：JavaFX WebView 加载失败。\n请确认网络可访问 jsDelivr CDN。",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private enum class LoadState { Loading, Ok, Failed }
