package com.windscore.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.windscore.core.model.Score
import com.windscore.core.parser.MusicXmlParser
import com.windscore.ui.components.PillButton
import com.windscore.ui.components.PillButtonStyle
import com.windscore.ui.i18n.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun ScoreFilePickerButton(
    onPicked: (Score) -> Unit,
    onFailure: (Throwable) -> Unit,
    modifier: Modifier,
    style: PillButtonStyle,
) {
    val openFile = rememberScoreFileOpener(onPicked, onFailure)
    PillButton(onClick = openFile, modifier = modifier, style = style) {
        androidx.compose.material3.Text(strings().openFile)
    }
}

@Composable
actual fun rememberScoreFileOpener(onPicked: (Score) -> Unit, onFailure: (Throwable) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnPicked = rememberUpdatedState(onPicked)
    val currentOnFailure = rememberUpdatedState(onFailure)
    fun handlePickedUri(uri: android.net.Uri?) {
        if (uri == null) return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = com.windscore.core.requireAndroidContext().contentResolver.openInputStream(uri)
                        ?.use { it.readBytes() }
                        ?: error("无法读取所选文件")
                    MusicXmlParser.parse(bytes)
                }
            }
            result.onSuccess { currentOnPicked.value(it) }
                .onFailure {
                    println("[打开文件] 解析失败: ${it.message}")
                    currentOnFailure.value(it)
                }
        }
    }
    // 主选：SAF OpenDocument（持久 URI）。回退：GET_CONTENT（兼容性更广）。
    val openDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        handlePickedUri(uri)
    }
    val getContentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        handlePickedUri(uri)
    }
    return remember(openDocLauncher, getContentLauncher, currentOnFailure) {
        {
            try {
                openDocLauncher.launch(arrayOf("*/*"))
            } catch (e: ActivityNotFoundException) {
                // 部分精简 ROM/模拟器没有 SAF 文档选择器，回退到 GET_CONTENT
                try {
                    getContentLauncher.launch("*/*")
                } catch (e2: Exception) {
                    println("[打开文件] 无可用文件选择器: ${e2.message}")
                    currentOnFailure.value(e2)
                }
            } catch (e: Exception) {
                println("[打开文件] OpenDocument 启动失败，回退 GET_CONTENT: ${e.message}")
                try {
                    getContentLauncher.launch("*/*")
                } catch (fallbackError: Exception) {
                    println("[打开文件] 无可用文件选择器: ${fallbackError.message}")
                    currentOnFailure.value(fallbackError)
                }
            }
        }
    }
}
