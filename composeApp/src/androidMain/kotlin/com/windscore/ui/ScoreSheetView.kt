package com.windscore.ui

import android.annotation.SuppressLint
import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream

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
) {
    val context = LocalContext.current
    var lastSyncedTick by remember { mutableLongStateOf(Long.MIN_VALUE) }
    val tickProvider by rememberUpdatedState(currentTickProvider)
    val onStepFwd by rememberUpdatedState(onStepForward)
    val onStepBack by rememberUpdatedState(onStepBackward)
    val seekHandler by rememberUpdatedState(onSeek)
    val osmdJsBase64 = remember {
        loadOsmdJsBase64(context)
            .also { if (it.isEmpty()) println("[OSMD] JS not found") }
    }

    val osmdJsBytes = remember(osmdJsBase64) {
        if (osmdJsBase64.isNotEmpty()) Base64.decode(osmdJsBase64, Base64.NO_WRAP) else ByteArray(0)
    }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val path = request?.url?.path ?: return null
                    return when {
                        path.contains("osmd.js") -> {
                            WebResourceResponse("application/javascript", "utf-8",
                                ByteArrayInputStream(osmdJsBytes))
                        }
                        path.contains("score.xml") -> {
                            WebResourceResponse("application/xml", "utf-8",
                                ByteArrayInputStream(rawXml.toByteArray(Charsets.UTF_8)))
                        }
                        else -> null
                    }
                }
            }
        }
    }

    LaunchedEffect(rawXml) {
        lastSyncedTick = Long.MIN_VALUE
        val html = OsmdHtml.buildHtml(divisions)
        webView.loadDataWithBaseURL("http://127.0.0.1:8080/", html, "text/html", "utf-8", null)
    }

    LaunchedEffect(rawXml) {
        var waited = 0
        while (waited < 90000) {
            delay(500)
            waited += 500
            webView.evaluateJavascript("(window.__renderTime||-1)") { result ->
                val ms = result?.removeSurrounding("\"")?.toDoubleOrNull()
                if (ms != null && ms >= 0) onRenderTime(ms)
            }
        }
    }

    LaunchedEffect(rawXml) {
        while (true) {
            val tick = tickProvider()
            if (tick < lastSyncedTick) {
                webView.evaluateJavascript("window.__resetCursor && window.__resetCursor();", null)
                lastSyncedTick = Long.MIN_VALUE
            }
            webView.evaluateJavascript("window.tickAndSeek && window.tickAndSeek($tick);") { result ->
                val cmd = result?.removeSurrounding("\"") ?: ""
                when {
                    cmd.startsWith("seek:") -> cmd.removePrefix("seek:").toLongOrNull()?.let { seekHandler(it) }
                    cmd.startsWith("step:") -> when (cmd.removePrefix("step:").trim()) {
                        "1" -> onStepFwd()
                        "-1" -> onStepBack()
                    }
                }
            }
            lastSyncedTick = tick
            delay(33)
        }
    }

    AndroidView(factory = { webView }, modifier = modifier.fillMaxSize())
}

private fun loadOsmdJsBase64(context: android.content.Context): String {
    val bytes = runCatching {
        context.assets.open("opensheetmusicdisplay.min.js").use { it.readBytes() }
    }.getOrNull() ?: return ""
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}
