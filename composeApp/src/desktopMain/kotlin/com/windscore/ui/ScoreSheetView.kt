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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.style.TextAlign
import com.sun.net.httpserver.HttpServer
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color as FxColor
import javafx.scene.web.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
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
    onSeek: (Long) -> Unit,
    onStepForward: () -> Unit,
    onStepBackward: () -> Unit,
    onSnapshot: ((SheetSnapshot) -> Unit)?,
    identityScore: com.windscore.core.model.Score?,
) {
    val tr = com.windscore.ui.i18n.strings()
    var state by remember { mutableStateOf<LoadState>(LoadState.Loading) }
    val webViewRef = remember { java.util.concurrent.atomic.AtomicReference<WebView?>(null) }
    val tickProvider by rememberUpdatedState(currentTickProvider)
    val onStepFwd by rememberUpdatedState(onStepForward)
    val onStepBack by rememberUpdatedState(onStepBackward)
    val snapshotHandler by rememberUpdatedState(onSnapshot)
    val renderDone = remember { AtomicBoolean(false) }
    val initFailed = remember { AtomicBoolean(false) }

    val serverInfo = remember {
        val osmdJsBytes = Thread.currentThread().contextClassLoader
            ?.getResourceAsStream("opensheetmusicdisplay.min.js")
            ?.use { it.readBytes() } ?: ByteArray(0)
        val data = SheetServerData(osmdJsBytes, rawXml, OsmdHtml.buildHtml(divisions, tr.loadingScore, tr.loadingFailed))
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

    LaunchedEffect(rawXml, identityScore) { serverData.xml = rawXml }
    LaunchedEffect(divisions, tr.loadingScore, tr.loadingFailed) {
        serverData.html = OsmdHtml.buildHtml(divisions, tr.loadingScore, tr.loadingFailed)
    }

    DisposableEffect(Unit) {
        onDispose { try { serverInfo.second.stop(0) } catch (_: Exception) {} }
    }

    SwingPanel(
        background = Color.White,
        factory = {
            try {
                try {
                    Platform.startup {}
                    Platform.setImplicitExit(false)
                } catch (_: IllegalStateException) {}
                val panel = JFXPanel()
                panel.border = javax.swing.BorderFactory.createEmptyBorder()
                panel.isFocusable = false
                Platform.runLater {
                    runCatching {
                        val webView = WebView()
                        webView.isContextMenuEnabled = false
                        webView.engine.isJavaScriptEnabled = true
                        val root = StackPane(webView)
                        root.style = "-fx-focus-color: transparent; -fx-faint-focus-color: transparent; -fx-background-insets: 0;"
                        panel.scene = Scene(root, FxColor.WHITE)
                        webViewRef.set(webView)
                        state = LoadState.Ok
                    }.onFailure {
                        println("[OSMD] WebView init failed: ${it.message}")
                        initFailed.set(true)
                        state = LoadState.Failed
                    }
                }
                panel
            } catch (t: Throwable) {
                initFailed.set(true)
                state = LoadState.Failed; JPanel()
            }
        },
        update = {}, modifier = modifier,
    )

    // Single LaunchedEffect: load -> poll -> cursor sync (在后台 Dispatchers.Default 运行，避免 latch.await 阻塞 Compose UI 线程导致动画卡顿)
    LaunchedEffect(rawXml, identityScore) {
        val uiContext = coroutineContext
        withContext(Dispatchers.Default) {
            renderDone.set(false)

            // Check cache
            val htmlText = OsmdHtml.buildHtml(divisions, tr.loadingScore, tr.loadingFailed)
            val cacheKey = sheetSnapshotCacheKey(rawXml, htmlText, serverData.osmdJsBytes, "javafx-23") + "_" + noteIdentityCacheSuffix(identityScore)
            val cacheDir = File(com.windscore.core.getCacheDir(), cacheKey)

            val handler = snapshotHandler
            if (handler != null && cacheDir.exists()) {
                val metaFile = File(cacheDir, "meta.json")
                if (metaFile.exists()) {
                    try {
                        val metaJson = metaFile.readText(Charsets.UTF_8)
                        val snapshot = parseSnapshotCache(cacheDir, metaJson, identityScore)
                        if (snapshot != null) {
                            withContext(uiContext) {
                                onRenderTime(0.0)
                                handler.invoke(snapshot)
                            }
                            renderDone.set(true)
                            println("[OSMD] Loaded SheetSnapshot from local cache: $cacheKey")
                            return@withContext
                        }
                    } catch (e: Exception) {
                        println("[OSMD] Failed to load cache: ${e.message}")
                    }
                }
            }

            var wv = webViewRef.get()
            while (wv == null && !initFailed.get()) { delay(100); wv = webViewRef.get() }
            if (initFailed.get() || wv == null) return@withContext
            val webView = wv // 定义只读 val 避免闭包内 Smart Cast 失败

            serverData.html = OsmdHtml.buildHtml(divisions, tr.loadingScore, tr.loadingFailed, importerProvenanceJson(rawXml, identityScore))
            serverData.xml = rawXml

            Platform.runLater { runCatching { webView.engine.load("http://127.0.0.1:$port/index.html") } }

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
                        val s = webView.engine.executeScript("(window.__status||'')")?.toString() ?: ""
                        if (s != lastStatus) { lastStatus = s; println("[OSMD] $s (${waited}ms)") }
                        val r = webView.engine.executeScript("(window.__renderTime||-1)")
                        if (r is Number) rt[0] = r.toDouble()
                    }
                    latch.countDown()
                }
                latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
                if (rt[0] >= 0) {
                    withContext(uiContext) {
                        onRenderTime(rt[0])
                    }
                    success = true
                    break
                }
            }
            renderDone.set(success)
            if (!success) return@withContext

            // 截图模式：等待 JS 端分块 SVG→PNG 完成
            if (handler != null) {
                var snapshotWaited = 0
                var snapshotDone = false
                while (snapshotWaited < 60000 && !snapshotDone) {
                    delay(300)
                    snapshotWaited += 300
                    val latch = java.util.concurrent.CountDownLatch(1)
                    val readyBox = intArrayOf(0, 0, 0, 0, -1) // [ready, count, width, totalHeight, generation]
                    Platform.runLater {
                        runCatching {
                            readyBox[0] = (webView.engine.executeScript("(window.__snapshotChunksReady||0)") as? Number)?.toInt() ?: 0
                            readyBox[1] = (webView.engine.executeScript("(window.__snapshotChunkCount||0)") as? Number)?.toInt() ?: 0
                            readyBox[2] = (webView.engine.executeScript("(window.__snapshotChunkWidth||0)") as? Number)?.toInt() ?: 0
                            readyBox[3] = (webView.engine.executeScript("(window.__snapshotTotalHeight||0)") as? Number)?.toInt() ?: 0
                            readyBox[4] = (webView.engine.executeScript("window.__snapshotGeneration") as? Number)?.toInt() ?: -1
                        }
                        latch.countDown()
                    }
                    latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
                    if (readyBox[1] > 0 && readyBox[0] >= readyBox[1]) {
                        snapshotDone = true
                        // 1. JavaFX 线程：一次性提取所有 data URL + 光标坐标
                        val urls = mutableListOf<String>()
                        for (ci in 0 until readyBox[1]) {
                            urls.add(runLaterAndGet {
                                webView.engine.executeScript("(window.__snapshotChunks[$ci]||'')")?.toString() ?: ""
                            })
                        }
                        val posJson = runLaterAndGet {
                            webView.engine.executeScript("JSON.stringify(window.__cursorPos||[])")?.toString() ?: "[]"
                        }
                        val timesJson = runLaterAndGet {
                            webView.engine.executeScript("JSON.stringify(window.__cursorTimes||[])")?.toString() ?: "[]"
                        }
                        // 2. 后台线程：解码 PNG（不阻塞 UI）
                        val chunks = mutableListOf<ImageBitmap>()
                        var firstH = 0
                        val rawPngBytesList = mutableListOf<ByteArray>()
                        for (url in urls) {
                            if (url.startsWith("data:image/png;base64,")) {
                                val bytes = java.util.Base64.getDecoder().decode(url.substringAfter("base64,"))
                                rawPngBytesList.add(bytes)
                                val bImg = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes))
                                if (firstH == 0) firstH = bImg.height
                                chunks.add(bImg.toComposeImageBitmap())
                            }
                        }
                        try {
                            val steps = parseCursorSteps(posJson, timesJson, divisions)
                            val geometryText = runLaterAndGet { webView.engine.executeScript("JSON.stringify(window.WindScoreNoteGeometry.forSnapshot(${readyBox[4]}))")?.toString() ?: "null" }
                            val geometry = decodeNoteGeometry(geometryText, identityScore, readyBox[2], readyBox[3], readyBox[4])
                            val snapshot = SheetSnapshot(chunks, readyBox[2], firstH, readyBox[3], steps, geometry)
                            
                            // Save to cache asynchronously
                            saveSnapshotCache(cacheDir, readyBox[2], firstH, readyBox[3], steps, rawPngBytesList, geometry)

                            withContext(uiContext) {
                                handler.invoke(snapshot)
                            }
                            println("[OSMD] Snapshot: ${readyBox[2]}x${readyBox[3]}, ${chunks.size} chunks, ${steps.size} steps")
                        } catch (e: Exception) {
                            println("[OSMD] Snapshot build failed: ${e.message}")
                        }
                    }
                }
                return@withContext
            }

            // 实时 WebView 模式（无截图回调时）：poll tickAndSeek
            var lastTick = Long.MIN_VALUE
            while (true) {
                val tick = tickProvider()
                if (tick < lastTick) {
                    Platform.runLater {
                        runCatching { webView.engine.executeScript("window.__resetCursor&&window.__resetCursor();") }
                    }
                    lastTick = Long.MIN_VALUE
                }
                // tickAndSeek advances cursor AND drains pending seek/step commands as a string
                val cmdBox = arrayOf("")
                val latch = java.util.concurrent.CountDownLatch(1)
                Platform.runLater {
                    runCatching {
                        val result = webView.engine.executeScript("window.tickAndSeek && window.tickAndSeek($tick);")
                        cmdBox[0] = result?.toString() ?: ""
                    }
                    latch.countDown()
                }
                latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
                val cmd = cmdBox[0]
                when {
                    cmd.startsWith("seek:") -> cmd.removePrefix("seek:").toLongOrNull()?.let {
                        withContext(uiContext) { onSeek(it) }
                    }
                    cmd.startsWith("step:") -> when (cmd.removePrefix("step:").trim()) {
                        "1" -> withContext(uiContext) { onStepFwd() }
                        "-1" -> withContext(uiContext) { onStepBack() }
                    }
                }
                lastTick = tick
                delay(33)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val wv = webViewRef.get()
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

private fun parseCursorSteps(posJson: String, timesJson: String, divisions: Int): List<CursorStep> {
    val posNums = posJson.removeSurrounding("[", "]")
        .split(",").mapNotNull { it.trim().toDoubleOrNull() }
    val timeNums = timesJson.removeSurrounding("[", "]")
        .split(",").mapNotNull { it.trim().toDoubleOrNull() }
    val steps = mutableListOf<CursorStep>()
    var pi = 0
    for (i in timeNums.indices) {
        val tick = Math.round(timeNums[i] * 4.0 * divisions)
        if (pi + 2 < posNums.size) {
            steps.add(CursorStep(tick, posNums[pi].toFloat(), posNums[pi + 1].toFloat(), posNums[pi + 2].toFloat()))
            pi += 3
        }
    }
    return steps
}

private fun <T> runLaterAndGet(block: () -> T): T {
    val result = java.util.concurrent.CompletableFuture<T>()
    Platform.runLater {
        runCatching(block)
            .onSuccess(result::complete)
            .onFailure(result::completeExceptionally)
    }
    return result.get(3, java.util.concurrent.TimeUnit.SECONDS)
}

private fun parseSnapshotCache(cacheDir: java.io.File, json: String, identityScore: com.windscore.core.model.Score?): SheetSnapshot? {
    try {
        val width = "\"width\"\\s*:\\s*(\\d+)".toRegex().find(json)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val firstH = "\"firstH\"\\s*:\\s*(\\d+)".toRegex().find(json)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val totalHeight = "\"totalHeight\"\\s*:\\s*(\\d+)".toRegex().find(json)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        
        val stepPattern = "\\{\\s*\"tick\"\\s*:\\s*(\\d+)\\s*,\\s*\"x\"\\s*:\\s*([\\d.-]+)\\s*,\\s*\"y\"\\s*:\\s*([\\d.-]+)\\s*,\\s*\"h\"\\s*:\\s*([\\d.-]+)\\s*\\}".toRegex()
        val steps = stepPattern.findAll(json).map { match ->
            val tick = match.groupValues[1].toLong()
            val x = match.groupValues[2].toFloat()
            val y = match.groupValues[3].toFloat()
            val h = match.groupValues[4].toFloat()
            CursorStep(tick, x, y, h)
        }.toList()
        
        val chunks = mutableListOf<androidx.compose.ui.graphics.ImageBitmap>()
        var index = 0
        while (true) {
            val chunkFile = java.io.File(cacheDir, "chunk_$index.png")
            if (!chunkFile.exists()) break
            val bytes = chunkFile.readBytes()
            val bImg = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes)) ?: return null
            chunks.add(bImg.toComposeImageBitmap())
            index++
        }
        if (chunks.isEmpty()) return null
        return SheetSnapshot(chunks, width, firstH, totalHeight, steps, readNoteGeometry(cacheDir, identityScore, width, totalHeight))
    } catch (e: Exception) {
        println("[OSMD] Error parsing cache: ${e.message}")
        return null
    }
}

private fun saveSnapshotCache(
    cacheDir: java.io.File,
    width: Int,
    firstH: Int,
    totalHeight: Int,
    steps: List<CursorStep>,
    rawPngBytesList: List<ByteArray>,
    geometry: NoteGeometrySnapshot?,
) {
    val cacheRoot = cacheDir.parentFile ?: return
    val tempDir = java.io.File(cacheRoot, ".${cacheDir.name}.${System.nanoTime()}.tmp")
    try {
        tempDir.mkdirs()
        
        // Write chunks
        for (i in rawPngBytesList.indices) {
            val chunkFile = java.io.File(tempDir, "chunk_$i.png")
            chunkFile.writeBytes(rawPngBytesList[i])
        }
        
        // Write meta.json
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"width\": ").append(width).append(",\n")
        sb.append("  \"firstH\": ").append(firstH).append(",\n")
        sb.append("  \"totalHeight\": ").append(totalHeight).append(",\n")
        sb.append("  \"steps\": [\n")
        for (i in steps.indices) {
            val step = steps[i]
            sb.append("    {\"tick\":").append(step.tick)
              .append(",\"x\":").append(step.x)
              .append(",\"y\":").append(step.y)
              .append(",\"h\":").append(step.height).append("}")
            if (i < steps.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ]\n")
        sb.append("}")
        
        val metaFile = java.io.File(tempDir, "meta.json")
        metaFile.writeText(sb.toString(), Charsets.UTF_8)
        writeNoteGeometry(tempDir, cacheDir.name, width, totalHeight, geometry)
        if (cacheDir.exists()) cacheDir.deleteRecursively()
        check(tempDir.renameTo(cacheDir)) { "Failed to publish snapshot cache" }
        trimSnapshotCaches(cacheRoot, cacheDir)
        println("[OSMD] Saved SheetSnapshot cache to disk: ${cacheDir.name}")
    } catch (e: Exception) {
        println("[OSMD] Failed to write cache: ${e.message}")
    } finally {
        if (tempDir.exists()) tempDir.deleteRecursively()
    }
}

private fun trimSnapshotCaches(root: java.io.File, current: java.io.File) {
    root.listFiles()
        ?.filter { it.isDirectory && it != current && !it.name.startsWith(".") }
        ?.sortedByDescending { it.lastModified() }
        ?.drop(7)
        ?.forEach { it.deleteRecursively() }
}
