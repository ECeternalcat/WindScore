package com.windscore.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun ScoreSheetView(
    rawXml: String,
    currentTickProvider: () -> Long,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
        }
    }

    LaunchedEffect(rawXml) {
        webView.loadDataWithBaseURL(
            "https://cdn.jsdelivr.net/",
            OsmdHtml.build(rawXml),
            "text/html",
            "utf-8",
            null,
        )
    }

    // currentTick 每向前推移，驱动 OSMD 光标步进/自动翻页（文档 4.4）
    var lastTick by remember { mutableLongStateOf(0L) }
    val tick = currentTickProvider()
    LaunchedEffect(tick) {
        if (tick > lastTick) {
            // 每个组（startTick）前进一步；近似同步，足够自动翻页
            webView.evaluateJavascript("window.__advanceCursor && window.__advanceCursor(1);", null)
        }
        lastTick = tick
    }

    AndroidView(factory = { webView }, modifier = modifier.fillMaxSize())
}
