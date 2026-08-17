package com.windscore.ui

import androidx.compose.runtime.Composable

@Composable
expect fun SettingsDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String = "Settings",
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
)
