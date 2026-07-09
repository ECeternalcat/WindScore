package com.windscore.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.windscore.core.model.Score
import com.windscore.core.parser.MusicXmlParser
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun ScoreFilePickerButton(
    onPicked: (Score) -> Unit,
    modifier: Modifier,
) {
    Button(
        onClick = {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.FILES_ONLY
                fileFilter = FileNameExtensionFilter("MusicXML (.xml / .mxl)", "xml", "mxl")
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                val file = chooser.selectedFile ?: return@Button
                runCatching {
                    val bytes = file.readBytes()
                    onPicked(MusicXmlParser.parse(bytes))
                }.onFailure { println("[打开文件] 解析失败: ${it.message}") }
            }
        },
        modifier = modifier,
    ) {
        Text("打开文件")
    }
}
