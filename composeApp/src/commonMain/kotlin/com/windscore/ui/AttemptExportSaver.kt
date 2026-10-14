package com.windscore.ui

import androidx.compose.runtime.Composable

enum class AttemptExportSaveResult { SAVED, CANCELLED, FAILED }

expect fun attemptExportAppVersion(): String

/** Opens a user-controlled native save destination and writes exactly these previewed bytes. */
@Composable
expect fun AttemptExportSaveButton(
    bytes: ByteArray,
    filename: String,
    label: String,
    enabled: Boolean,
    onSavingChanged: (Boolean) -> Unit,
    onResult: (AttemptExportSaveResult) -> Unit,
)
