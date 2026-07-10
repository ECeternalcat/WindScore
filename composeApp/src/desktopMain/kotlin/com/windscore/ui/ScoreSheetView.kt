package com.windscore.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.sun.net.httpserver.HttpServer
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color as FxColor
import javafx.scene.web.WebView
import kotlinx.coroutines.delay
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JPanel

private class SheetServerData(
    val osmdJsBytes: ByteArray,
    @Volatile var xml: String,
    @Volatile var html: String,
)

@Composable
actual fun ScoreSheetView(
    rawXml: String,
    currentTickProvider: () -> Long,
    modifier: Modifier,
    divisions: Int,
    pages: Int,
    onRenderTime: (Double) -> Unit,
) {
    var state by remember { mutableStateOf<LoadState>(LoadState.Loading) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val tickProvider by rememberUpdatedState(currentTickProvider)
    val renderDone = remember { AtomicBoolean(false) }

    val serverInfo = remember {
        val osmdJsBytes = Thread.currentThread().contextClassLoader
            ?.getResourceAsStream("opensheetmusicdisplay.min.js")
            ?.use { it.readBytes() } ?: ByteArray(0)
        val data = SheetServerData(osmdJsBytes, rawXml, OsmdHtml.buildHtml(divisions))
        val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        srv.createContext("/osmd.js") { ex ->
            ex.responseHeaders["Content-Type"] = "application/javascript"
            ex.sendResponseHeaders(200, data.osmdJsBytes.size.toLong())
            ex.responseBody.write(data.osmdJsBytes); ex.responseBody.close()
        }
        srv.createContext("/score.xml") { ex ->
            val b = data.xml.toByteArray(Charsets.UTF_8)
            ex.responseHeaders["Content-Type"] = "application/xml"
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.write(b); ex.responseBody.close()
        }
        srv.createContext("/") { ex ->
            val b = data.html.toByteArray(Charsets.UTF_8)
            ex.responseHeaders["Content-Type"] = "text/html; charset=utf-8"
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.write(b); ex.responseBody.close()
        }
        srv.start()
        println("[OSMD] HttpServer port=${srv.address.port}")
        data to srv
    }
    val serverData = serverInfo.first
    val port = serverInfo.second.address.port

    LaunchedEffect(rawXml) { serverData.xml = rawXml }
    LaunchedEffect(divisions) { serverData.html = OsmdHtml.buildHtml(divisions) }

    DisposableEffect(Unit) {
        onDispose { try { serverInfo.second.stop(0) } catch (_: Exception) {} }
    }

    SwingPanel(
        background = Color.White,
        factory = {
            try {
                try { Platform.startup {} } catch (_: IllegalStateException) {}
                val panel = JFXPanel()
                Platform.runLater {
                    runCatching {
                        val webView = WebView()
                        webView.isContextMenuEnabled = false
                        webView.engine.isJavaScriptEnabled = true
                        panel.scene = Scene(StackPane(webView), FxColor.WHITE)
                        webViewRef = webView
                        state = LoadState.Ok
                    }.onFailure {
                        println("[OSMD] WebView init failed: ${it.message}")
                        state = LoadState.Failed
                    }
                }
                panel
            } catch (t: Throwable) {
                state = LoadState.Failed; JPanel()
            }
        },
        update = {}, modifier = modifier,
    )

    // Single LaunchedEffect: load -> poll -> cursor sync
    LaunchedEffect(rawXml) {
        renderDone.set(false)
        var wv = webViewRef
        while (wv == null) { delay(100); wv = webViewRef }
        serverData.html = OsmdHtml.buildHtml(divisions)
        serverData.xml = rawXml

        Platform.runLater { runCatching { wv.engine.load("http://127.0.0.1:$port/index.html") } }

        var waited = 0
        var lastStatus = ""
        var success = false
        while (waited < 90000) {
            delay(500)
            waited += 500
            val latch = java.util.concurrent.CountDownLatch(1)
            val rt = doubleArrayOf(-1.0)
            Platform.runLater {
                runCatching {
                    val s = wv.engine.executeScript("(window.__status||'')")?.toString() ?: ""
                    if (s != lastStatus) { lastStatus = s; println("[OSMD] $s (${waited}ms)") }
                    val r = wv.engine.executeScript("(window.__renderTime||-1)")
                    if (r is Number) rt[0] = r.toDouble()
                }
                latch.countDown()
            }
            latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
            if (rt[0] >= 0) {
                onRenderTime(rt[0])
                success = true
                break
            }
        }
        renderDone.set(success)
        if (!success) return@LaunchedEffect

        // Cursor sync starts after render is done
        var lastTick = Long.MIN_VALUE
        while (true) {
            val tick = tickProvider()
            if (tick != lastTick) {
                if (tick < lastTick) {
                    Platform.runLater {
                        runCatching { wv.engine.executeScript("window.__resetCursor&&window.__resetCursor();") }
                    }
                    lastTick = Long.MIN_VALUE
                }
                lastTick = tick
                Platform.runLater {
                    runCatching { wv.engine.executeScript("window.moveToTick&&window.moveToTick($tick);") }
                }
            }
            delay(33)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val wv = webViewRef
            if (wv != null) Platform.runLater { runCatching { wv.engine.loadContent(null) } }
        }
    }

    if (state == LoadState.Failed) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Sheet mode unavailable: JavaFX WebView failed.",
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private enum class LoadState { Loading, Ok, Failed }
