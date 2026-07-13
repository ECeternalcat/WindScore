package com.windscore.ui

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

@Composable
actual fun ScoreFilePickerButton(
    onPicked: (Score) -> Unit,
    modifier: Modifier,
    style: PillButtonStyle,
) {
    val openFile = rememberScoreFileOpener(onPicked)
    PillButton(onClick = openFile, modifier = modifier, style = style) {
        androidx.compose.material3.Text("打开文件")
    }
}

@Composable
actual fun rememberScoreFileOpener(onPicked: (Score) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val bytes = AndroidApp.appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) onPicked(MusicXmlParser.parse(bytes))
        }.onFailure { println("[打开文件] 解析失败: ${it.message}") }
    }
    return remember(launcher) {
        { launcher.launch(arrayOf("application/xml", "text/xml", "application/vnd.recordare.musicxml", "*/*")) }
    }
}
