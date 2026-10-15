package com.windscore.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.windscore.ViewMode
import com.windscore.ui.components.CircularIconButton
import com.windscore.ui.i18n.strings
import org.jetbrains.compose.resources.painterResource
import windscore.composeapp.generated.resources.*

/** Same choices in compact controls, desktop sidebar and scoring setup. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ViewModeButton(mode: ViewMode, selected: Boolean, onClick: () -> Unit) {
    val s = strings()
    val label = when(mode) {
        ViewMode.ROLL -> s.pianoRoll
        ViewMode.SHEET_KEYBOARD -> s.readSheet
        ViewMode.SHEET_FULL -> s.viewSheet
    }
    val icon = when(mode) {
        ViewMode.ROLL -> Res.drawable.piano_keys_thin
        ViewMode.SHEET_KEYBOARD -> Res.drawable.book_open_text_thin
        ViewMode.SHEET_FULL -> Res.drawable.music_notes_thin
    }
    val tag = when(mode) {
        ViewMode.ROLL -> "view-roll"
        ViewMode.SHEET_KEYBOARD -> "view-sheet-keyboard"
        ViewMode.SHEET_FULL -> "view-sheet-full"
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        CircularIconButton(onClick=onClick, selected=selected, testTag=tag) {
            Icon(painterResource(icon),label,Modifier.size(20.dp))
        }
    }
}
