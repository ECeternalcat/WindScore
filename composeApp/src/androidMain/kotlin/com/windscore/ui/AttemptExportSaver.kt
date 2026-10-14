package com.windscore.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.windscore.core.collection.AttemptExportSaveTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

actual fun attemptExportAppVersion(): String = "1.0"

@Composable
actual fun AttemptExportSaveButton(
    bytes: ByteArray,
    filename: String,
    label: String,
    enabled: Boolean,
    onSavingChanged: (Boolean) -> Unit,
    onResult: (AttemptExportSaveResult) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val transaction = remember { AttemptExportSaveTransaction() }
    var saving by remember { mutableStateOf(false) }
    fun complete(result: AttemptExportSaveResult) {
        transaction.finish()
        saving = false
        onSavingChanged(false)
        onResult(result)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) {
            complete(AttemptExportSaveResult.CANCELLED)
        } else {
            val snapshot = transaction.payload()
            if (snapshot == null) {
                complete(AttemptExportSaveResult.FAILED)
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                val outcome = try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(snapshot) }
                            ?: error("Could not open selected destination")
                    }
                    AttemptExportSaveResult.SAVED
                } catch (_: CancellationException) {
                    AttemptExportSaveResult.FAILED
                } catch (_: Exception) {
                    AttemptExportSaveResult.FAILED
                }
                complete(outcome)
            }
        }
    }

    Button(onClick = {
        if (!saving && transaction.begin(bytes)) {
            saving = true
            onSavingChanged(true)
            try {
                launcher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, filename)
                })
            } catch (_: Exception) {
                complete(AttemptExportSaveResult.FAILED)
            }
        }
    }, enabled = enabled && !saving) { Text(if (saving) "$label…" else label) }
}
