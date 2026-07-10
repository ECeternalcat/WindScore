package com.windscore.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.windscore.core.model.Score
import com.windscore.ui.components.PillButtonStyle

/**
 * 平台本地的“打开乐谱文件”按钮（文档 Phase 2 Step 3）。
 * - Desktop：JFileChooser 文件对话框。
 * - Android：SAF (Storage Access Framework) 文档选择器。
 * 选中后解析为 [Score] 并回调。
 */
@Composable
expect fun ScoreFilePickerButton(
    onPicked: (Score) -> Unit,
    modifier: Modifier = Modifier,
    style: PillButtonStyle = PillButtonStyle.SECONDARY,
)

