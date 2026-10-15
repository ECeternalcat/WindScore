package com.windscore.ui

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.windscore.core.getCacheDir
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@SuppressLint("SetJavaScriptEnabled")
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
) {
    val context = LocalContext.current
    val tr = com.windscore.ui.i18n.strings()
    val snapshotHandler by rememberUpdatedState(onSnapshot)
    val osmdJsBase64 = remember {
        loadOsmdJsBase64(context)
            .also { if (it.isEmpty()) println("[OSMD] JS not found") }
    }

    val osmdJsBytes = remember(osmdJsBase64) {
        if (osmdJsBase64.isNotEmpty()) Base64.decode(osmdJsBase64, Base64.NO_WRAP) else ByteArray(0)
    }
    val servedOsmdBytes = remember { AtomicReference(osmdJsBytes) }
    val servedXmlBytes = remember { AtomicReference(rawXml.toByteArray(Charsets.UTF_8)) }
    servedOsmdBytes.set(osmdJsBytes)
    servedXmlBytes.set(rawXml.toByteArray(Charsets.UTF_8))

    val webView = remember(context) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val path = request?.url?.path ?: return null
                    return when {
                        path.contains("osmd.js") -> {
                            WebResourceResponse("application/javascript", "utf-8",
                                ByteArrayInputStream(servedOsmdBytes.get()))
                        }
                        path.contains("score.xml") -> {
                            WebResourceResponse("application/xml", "utf-8",
                                ByteArrayInputStream(servedXmlBytes.get()))
                        }
                        else -> null
                    }
                }
            }
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }
    }

    // 渲染流程：先查缓存 → 命中则直接回传；否则加载 WebView 渲染、截图、回传并写缓存。
    LaunchedEffect(rawXml) {
        val handler = snapshotHandler
        val htmlText = OsmdHtml.buildHtml(divisions, tr.loadingScore, tr.loadingFailed)
        val cacheKey = sheetSnapshotCacheKey(rawXml, htmlText, osmdJsBytes, "android-webview")
        val cacheDir = File(getCacheDir(), cacheKey)

        // 1. 缓存命中：直接回传，跳过 WebView 渲染
        if (handler != null && File(cacheDir, "meta.json").exists()) {
            val snapshot = runCatching {
                parseSnapshotCache(cacheDir, File(cacheDir, "meta.json").readText(Charsets.UTF_8))
            }.getOrNull()
            if (snapshot != null) {
                onRenderTime(0.0)
                handler(snapshot)
                println("[OSMD] Loaded SheetSnapshot from cache: $cacheKey")
                return@LaunchedEffect
            }
        }

        // 2. 缓存未命中：加载页面
        webView.loadDataWithBaseURL("http://127.0.0.1:8080/", htmlText, "text/html", "utf-8", null)

        // 3. 等待 OSMD 渲染完成
        var waited = 0
        var rendered = false
        while (waited < 90000 && !rendered) {
            delay(500)
            waited += 500
            val rt = webView.evalJs("(window.__renderTime||-1)")
            val ms = rt?.unquote()?.toDoubleOrNull()
            if (ms != null && ms >= 0) {
                onRenderTime(ms)
                rendered = true
            }
        }
        if (!rendered) {
            println("[OSMD] render timed out after ${waited}ms")
            return@LaunchedEffect
        }
        if (handler == null) return@LaunchedEffect

        // 4. 等待 JS 端分块 SVG→PNG 完成
        var sw = 0
        while (sw < 60000) {
            delay(300)
            sw += 300
            val ready = webView.evalJs("(window.__snapshotChunksReady||0)")?.unquote()?.toIntOrNull() ?: 0
            val count = webView.evalJs("(window.__snapshotChunkCount||0)")?.unquote()?.toIntOrNull() ?: 0
            if (count > 0 && ready >= count) {
                val width = webView.evalJs("(window.__snapshotChunkWidth||0)")?.unquote()?.toIntOrNull() ?: 0
                val totalH = webView.evalJs("(window.__snapshotTotalHeight||0)")?.unquote()?.toIntOrNull() ?: 0
                val chunks = mutableListOf<ImageBitmap>()
                val rawPngBytesList = mutableListOf<ByteArray>()
                var firstH = 0
                for (ci in 0 until count) {
                    val url = webView.evalJs("(window.__snapshotChunks[$ci]||'')")?.unquote() ?: ""
                    val bytes = decodeDataUrlPng(url) ?: continue
                    rawPngBytesList.add(bytes)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                    if (firstH == 0) firstH = bmp.height
                    chunks.add(bmp.asImageBitmap())
                }
                if (chunks.isNotEmpty()) {
                    val posJson = webView.evalJs("JSON.stringify(window.__cursorPos||[])")?.unquote() ?: "[]"
                    val timesJson = webView.evalJs("JSON.stringify(window.__cursorTimes||[])")?.unquote() ?: "[]"
                    val steps = parseCursorSteps(posJson, timesJson, divisions)
                    handler(SheetSnapshot(chunks, width, firstH, totalH, steps))
                    println("[OSMD] Snapshot: ${width}x${totalH}, ${chunks.size} chunks, ${steps.size} steps")
                    // 5. 写入缓存
                    saveSnapshotCache(cacheDir, width, firstH, totalH, steps, rawPngBytesList)
                } else {
                    val err = webView.evalJs("(window.__snapshotError||'')")?.unquote() ?: ""
                    println("[OSMD] Snapshot produced no chunks (err=$err)")
                }
                return@LaunchedEffect
            }
        }
        println("[OSMD] snapshot chunks never ready after ${sw}ms")
    }

    AndroidView(factory = { webView }, modifier = modifier.fillMaxSize())
}

/** 把 evaluateJavascript 的回调包成挂起函数。返回值为 JS 值的 JSON 字面（字符串带引号）。 */
private suspend fun WebView.evalJs(script: String): String? = suspendCancellableCoroutine { cont ->
    evaluateJavascript(script) { result ->
        if (cont.isActive) cont.resumeWith(Result.success(result))
    }
}

/** 去除 evaluateJavascript 对字符串结果额外加的双引号；数字/裸值不受影响。 */
private fun String.unquote(): String = removeSurrounding("\"")

private fun decodeDataUrlPng(url: String): ByteArray? {
    if (!url.startsWith("data:image/png;base64,")) return null
    val b64 = url.substringAfter("data:image/png;base64,")
    return runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull()
}

private fun parseCursorSteps(posJson: String, timesJson: String, divisions: Int): List<CursorStep> {
    val posNums = posJson.removeSurrounding("[", "]").split(",").mapNotNull { it.trim().toDoubleOrNull() }
    val timeNums = timesJson.removeSurrounding("[", "]").split(",").mapNotNull { it.trim().toDoubleOrNull() }
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

private fun loadOsmdJsBase64(context: android.content.Context): String {
    val bytes = runCatching {
        context.assets.open("opensheetmusicdisplay.min.js").use { it.readBytes() }
    }.getOrNull() ?: return ""
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

// —— 快照缓存（与 Desktop 同格式：chunk_*.png + meta.json，按 xmlHash_htmlHash 命名）——

private fun parseSnapshotCache(cacheDir: File, json: String): SheetSnapshot? {
    return try {
        val width = "\"width\"\\s*:\\s*(\\d+)".toRegex().find(json)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val firstH = "\"firstH\"\\s*:\\s*(\\d+)".toRegex().find(json)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val totalHeight = "\"totalHeight\"\\s*:\\s*(\\d+)".toRegex().find(json)?.groupValues?.get(1)?.toIntOrNull() ?: return null

        val stepPattern = "\\{\\s*\"tick\"\\s*:\\s*(\\d+)\\s*,\\s*\"x\"\\s*:\\s*([\\d.-]+)\\s*,\\s*\"y\"\\s*:\\s*([\\d.-]+)\\s*,\\s*\"h\"\\s*:\\s*([\\d.-]+)\\s*\\}".toRegex()
        val steps = stepPattern.findAll(json).map { match ->
            CursorStep(
                match.groupValues[1].toLong(),
                match.groupValues[2].toFloat(),
                match.groupValues[3].toFloat(),
                match.groupValues[4].toFloat(),
            )
        }.toList()

        val chunks = mutableListOf<ImageBitmap>()
        var index = 0
        while (true) {
            val chunkFile = File(cacheDir, "chunk_$index.png")
            if (!chunkFile.exists()) break
            val bytes = chunkFile.readBytes()
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            chunks.add(bmp.asImageBitmap())
            index++
        }
        if (chunks.isEmpty()) return null
        SheetSnapshot(chunks, width, firstH, totalHeight, steps)
    } catch (e: Exception) {
        println("[OSMD] Error parsing cache: ${e.message}")
        null
    }
}

private fun saveSnapshotCache(
    cacheDir: File,
    width: Int,
    firstH: Int,
    totalHeight: Int,
    steps: List<CursorStep>,
    rawPngBytesList: List<ByteArray>,
) {
    val cacheRoot = cacheDir.parentFile ?: return
    val tempDir = File(cacheRoot, ".${cacheDir.name}.${System.nanoTime()}.tmp")
    try {
        tempDir.mkdirs()

        for (i in rawPngBytesList.indices) {
            File(tempDir, "chunk_$i.png").writeBytes(rawPngBytesList[i])
        }

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

        File(tempDir, "meta.json").writeText(sb.toString(), Charsets.UTF_8)
        if (cacheDir.exists()) cacheDir.deleteRecursively()
        check(tempDir.renameTo(cacheDir)) { "Failed to publish snapshot cache" }
        trimSnapshotCaches(cacheRoot, cacheDir)
        println("[OSMD] Saved SheetSnapshot cache: ${cacheDir.name}")
    } catch (e: Exception) {
        println("[OSMD] Failed to write cache: ${e.message}")
    } finally {
        if (tempDir.exists()) tempDir.deleteRecursively()
    }
}

private fun trimSnapshotCaches(root: File, current: File) {
    root.listFiles()
        ?.filter { it.isDirectory && it != current && !it.name.startsWith(".") }
        ?.sortedByDescending { it.lastModified() }
        ?.drop(7)
        ?.forEach { it.deleteRecursively() }
}
