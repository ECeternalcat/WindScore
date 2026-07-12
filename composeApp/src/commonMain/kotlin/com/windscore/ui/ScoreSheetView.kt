package com.windscore.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 看谱模式（文档 4.4）：WebView 加载 OSMD 渲染五线谱。
 * 桌面端渲染完成后截图，后续由 NativeSheetView 接管显示。
 */
@Composable
expect fun ScoreSheetView(
    rawXml: String,
    currentTickProvider: () -> Long,
    modifier: Modifier = Modifier,
    divisions: Int = 4,
    pages: Int = 1,
    onRenderTime: (Double) -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onStepForward: () -> Unit = {},
    onStepBackward: () -> Unit = {},
    onSnapshot: ((SheetSnapshot) -> Unit)? = null,
)
