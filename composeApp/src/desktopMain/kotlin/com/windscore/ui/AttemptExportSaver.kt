package com.windscore.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.windscore.core.collection.AttemptExportSaveTransaction
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

actual fun attemptExportAppVersion(): String = "1.0.0"

@Composable
actual fun AttemptExportSaveButton(
    bytes: ByteArray,
    filename: String,
    label: String,
    enabled: Boolean,
    onSavingChanged: (Boolean) -> Unit,
    onResult: (AttemptExportSaveResult) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val transaction = remember { AttemptExportSaveTransaction() }
    var busy by remember { mutableStateOf(false) }
    fun complete(result: AttemptExportSaveResult) {
        transaction.finish()
        busy = false
        onSavingChanged(false)
        onResult(result)
    }

    Button(onClick = {
        if (!busy && transaction.begin(bytes)) {
            busy = true
            onSavingChanged(true)
            // Capture occurred synchronously above, before opening the native chooser.
            try {
                EventQueue.invokeLater {
                try {
                    val dialog = FileDialog(null as Frame?, "Save practice collection", FileDialog.SAVE)
                    dialog.file = filename
                    dialog.isVisible = true
                    val directory = dialog.directory
                    val name = dialog.file
                    if (directory == null || name == null) {
                        complete(AttemptExportSaveResult.CANCELLED)
                    } else {
                        val snapshot = transaction.payload()!!
                        scope.launch {
                            val result = try {
                                withContext(Dispatchers.IO) { File(directory, name).writeBytes(snapshot) }
                                AttemptExportSaveResult.SAVED
                            } catch (_: CancellationException) {
                                AttemptExportSaveResult.FAILED
                            } catch (_: Exception) {
                                AttemptExportSaveResult.FAILED
                            }
                            complete(result)
                        }
                    }
                } catch (_: Exception) {
                    complete(AttemptExportSaveResult.FAILED)
                }
                }
            } catch (_: Exception) {
                complete(AttemptExportSaveResult.FAILED)
            }
        }
    }, enabled = enabled && !busy) { Text(label) }
}
