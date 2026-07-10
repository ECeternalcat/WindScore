package com.windscore.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.windscore.core.model.Score
import com.windscore.core.parser.MusicXmlParser
import com.windscore.ui.components.PillButton
import com.windscore.ui.components.PillButtonStyle
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun ScoreFilePickerButton(
    onPicked: (Score) -> Unit,
    modifier: Modifier,
    style: PillButtonStyle,
) {
    PillButton(
        onClick = {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.FILES_ONLY
                fileFilter = FileNameExtensionFilter("MusicXML (.xml / .mxl)", "xml", "mxl")
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                val file = chooser.selectedFile ?: return@PillButton
                runCatching {
                    val bytes = file.readBytes()
                    onPicked(MusicXmlParser.parse(bytes))
                }.onFailure { println("[打开文件] 解析失败: ${it.message}") }
            }
        },
        modifier = modifier,
        style = style,
    ) {
        androidx.compose.material3.Text("打开文件")
    }
}
