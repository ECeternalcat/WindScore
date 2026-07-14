package com.windscore.ui

import androidx.compose.runtime.Composable

@Composable
expect fun SettingsDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
)
