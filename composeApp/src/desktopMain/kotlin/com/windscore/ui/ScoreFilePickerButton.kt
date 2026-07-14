package com.windscore.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.windscore.core.model.Score
import com.windscore.core.parser.MusicXmlParser
import com.windscore.ui.components.PillButton
import com.windscore.ui.components.PillButtonStyle
import com.windscore.ui.i18n.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.swing.SwingUtilities

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

@Composable
actual fun rememberScoreFileOpener(onPicked: (Score) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val title = strings().openFile
    return remember(onPicked, title) {
        {
            scope.launch(Dispatchers.IO) {
                // 优先使用 PowerShell 调用现代 Windows 文件对话框（IFileOpenDialog）
                val filePath = showPowerShellFileDialog(title)
                // 回退到 AWT FileDialog（原生 Win32 对话框）
                    ?: showAwtFileDialog(title)
                if (filePath != null) {
                    runCatching {
                        val bytes = File(filePath).readBytes()
                        val score = MusicXmlParser.parse(bytes)
                        SwingUtilities.invokeLater { onPicked(score) }
                    }.onFailure { println("[打开文件] 解析失败: ${it.message}"); it.printStackTrace() }
                }
            }
        }
    }
}

/**
 * PowerShell System.Windows.Forms.OpenFileDialog — Windows 10/11 原生文件对话框。
 * 使用 -STA 模式（Windows Forms 要求单线程单元）。
 */
private fun showPowerShellFileDialog(title: String): String? {
    return try {
        val script = """
            Add-Type -AssemblyName System.Windows.Forms
            ${'$'}d = New-Object System.Windows.Forms.OpenFileDialog
            ${'$'}d.Filter = 'MusicXML (*.xml;*.mxl)|*.xml;*.mxl|All files (*.*)|*.*'
            ${'$'}d.Title = '$title'
            ${'$'}d.AutoUpgradeEnabled = ${'$'}true
            if (${'$'}d.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {
                [Console]::Out.WriteLine(${'$'}d.FileName)
            }
        """.trimIndent()
        val pb = ProcessBuilder("powershell", "-NoProfile", "-STA", "-Command", script)
        val process = pb.start()
        // 读取 stdout（文件路径）
        val stdout = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        if (stdout.isNotEmpty()) stdout else null
    } catch (e: Exception) {
        println("[打开文件] PowerShell dialog failed: ${e.message}")
        null
    }
}

/**
 * AWT FileDialog 回退方案 — 使用 Win32 GetOpenFileName 原生对话框。
 */
private fun showAwtFileDialog(title: String): String? {
    var result: String? = null
    try {
        SwingUtilities.invokeAndWait {
            val parent = java.awt.Window.getWindows().firstOrNull { it.isVisible } as? java.awt.Frame
            val dialog = java.awt.FileDialog(parent, title, java.awt.FileDialog.LOAD)
            dialog.setFilenameFilter { _, name -> name.endsWith(".xml", true) || name.endsWith(".mxl", true) }
            dialog.isVisible = true
            val files = dialog.files
            if (files != null && files.isNotEmpty()) {
                result = files[0].absolutePath
            }
        }
    } catch (e: Exception) {
        println("[打开文件] AWT dialog failed: ${e.message}")
    }
    return result
}
