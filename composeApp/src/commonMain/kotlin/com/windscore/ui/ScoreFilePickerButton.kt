package com.windscore.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.windscore.core.model.Score
import com.windscore.ui.components.PillButtonStyle

@Composable
expect fun ScoreFilePickerButton(
    onPicked: (Score) -> Unit,
    modifier: Modifier = Modifier,
    style: PillButtonStyle = PillButtonStyle.SECONDARY,
)

/**
 * 返回一个 click handler，点击时弹出系统文件选择框。
 * 用于自定义 UI 中触发文件选择（与 ActionButton.onClick 无缝对接）。
 */
@Composable
expect fun rememberScoreFileOpener(onPicked: (Score) -> Unit): () -> Unit


