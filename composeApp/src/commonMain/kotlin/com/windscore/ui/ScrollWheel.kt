package com.windscore.ui

import androidx.compose.ui.Modifier

/**
 * 平台鼠标滚轮处理：桌面端捕获滚轮事件，Android 端无操作（触屏滚动由拖拽手势处理）。
 */
internal expect fun Modifier.scrollWheelHandler(onScroll: (Float) -> Unit): Modifier
