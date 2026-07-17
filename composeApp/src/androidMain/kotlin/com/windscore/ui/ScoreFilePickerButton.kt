package com.windscore.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.windscore.AndroidApp
import com.windscore.core.model.Score
import com.windscore.core.parser.MusicXmlParser
import com.windscore.ui.components.PillButton
import com.windscore.ui.components.PillButtonStyle
import com.windscore.ui.i18n.strings

@Composable
actual fun ScoreFilePickerButton(
    onPicked: (Score) -> Unit,
    modifier: Modifier,
    style: PillButtonStyle,
) {
    val openFile = rememberScoreFileOpener(onPicked)
    PillButton(onClick = openFile, modifier = modifier, style = style) {
        androidx.compose.material3.Text(strings().openFile)
    }
}

/** 读取并解析选中的乐谱文件；任何异常都被吞掉并打印，避免崩溃。 */
private fun handlePickedUri(uri: android.net.Uri?, onPicked: (Score) -> Unit) {
    if (uri == null) return
    runCatching {
        val bytes = AndroidApp.appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes != null) onPicked(MusicXmlParser.parse(bytes))
    }.onFailure { println("[打开文件] 解析失败: ${it.message}") }
}

@Composable
actual fun rememberScoreFileOpener(onPicked: (Score) -> Unit): () -> Unit {
    // 主选：SAF OpenDocument（持久 URI）。回退：GET_CONTENT（兼容性更广）。
    val openDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        handlePickedUri(uri, onPicked)
    }
    val getContentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        handlePickedUri(uri, onPicked)
    }
    return remember(openDocLauncher, getContentLauncher) {
        {
            try {
                openDocLauncher.launch(arrayOf("*/*"))
            } catch (e: ActivityNotFoundException) {
                // 部分精简 ROM/模拟器没有 SAF 文档选择器，回退到 GET_CONTENT
                try {
                    getContentLauncher.launch("*/*")
                } catch (e2: Exception) {
                    println("[打开文件] 无可用文件选择器: ${e2.message}")
                }
            } catch (e: Exception) {
                println("[打开文件] OpenDocument 启动失败，回退 GET_CONTENT: ${e.message}")
                try {
                    getContentLauncher.launch("*/*")
                } catch (_: Exception) {
                    // 真的没有可用的选择器，静默忽略
                }
            }
        }
    }
}
