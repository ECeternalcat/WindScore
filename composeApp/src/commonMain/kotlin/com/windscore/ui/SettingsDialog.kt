package com.windscore.ui

import androidx.compose.runtime.Composable

@Composable
expect fun SettingsDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String = "Settings / 设置",
    content: @Composable () -> Unit
)
