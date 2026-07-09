package com.windscore.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 传统看谱模式（文档 4.4）：用 WebView 加载 OSMD 渲染五线谱并自动翻页。
 *
 * **实验性（Phase 2 Step 4，独立尝试）**：
 * - Android：AndroidView + WebView，加载 OSMD（CDN），渲染 [rawXml]，
 *   currentTick 每推进一步调用 `osmd.cursor.next()` 实现光标自动步进/翻页。
 * - Desktop：Compose Desktop 无内置 WebView（需引入 JavaFX），暂以占位提示。
 *
 * 严禁用 Compose 手写五线谱排版——直接复用 OSMD 的避让与翻页算法。
 *
 * @param rawXml 原始 MusicXML 文本
 * @param currentTickProvider 当前游标 tick（draw/读取阶段回调）
 */
@Composable
expect fun ScoreSheetView(
    rawXml: String,
    currentTickProvider: () -> Long,
    modifier: Modifier = Modifier,
)
