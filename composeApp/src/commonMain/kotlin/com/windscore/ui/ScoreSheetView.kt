package com.windscore.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 传统看谱模式（文档 4.4）：WebView 加载 OSMD 渲染五线谱，滚动 + 光标追焦。
 *
 * 交互（文档 3.3）：
 * - 点击小节 → 宏观吸附到小节开头（[onSeek]）。
 * - 按住后左右滑动 → 按音符步进微观盲操（[onStepForward] / [onStepBackward]）。
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
)
