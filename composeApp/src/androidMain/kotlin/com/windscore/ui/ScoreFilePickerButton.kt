package com.windscore.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
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
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val bytes = AndroidApp.appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) onPicked(MusicXmlParser.parse(bytes))
        }.onFailure { println("[打开文件] 解析失败: ${it.message}") }
    }
    PillButton(
        onClick = {
            // 覆盖常见 MusicXML MIME；末尾 */* 兜底
            launcher.launch(arrayOf("application/xml", "text/xml", "application/vnd.recordare.musicxml", "*/*"))
        },
        modifier = modifier,
        style = style,
    ) {
        androidx.compose.material3.Text("打开文件")
    }
}
