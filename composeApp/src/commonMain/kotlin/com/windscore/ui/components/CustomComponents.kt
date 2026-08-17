package com.windscore.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.windscore.ui.theme.windScoreColors
import com.windscore.ui.theme.WindScoreRounded

@Composable
fun CustomSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val background = if (checked) windScoreColors().primary else windScoreColors().hairlineSoft
    val alignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier = modifier
            .bounceClick(interactionSource)
            .width(40.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onCheckedChange(!checked) }
            )
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White)
                .align(alignment)
        )
    }
}

@Composable
fun PillToggle(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    PillButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        style = if (selected) PillButtonStyle.PRIMARY else PillButtonStyle.SECONDARY,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600, fontSize = 12.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CircularIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    testTag: String? = null,
    content: @Composable () -> Unit,
) {
    val background = when {
        selected -> windScoreColors().primary
        else -> windScoreColors().surfaceSoft
    }
    val contentColor = when {
        selected -> windScoreColors().onPrimary
        else -> windScoreColors().ink
    }
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .semantics { this.selected = selected }
            .bounceClick(interactionSource)
            .size(40.dp)
            .clip(RoundedCornerShape(WindScoreRounded.full))
            .background(if (enabled) background else windScoreColors().hairlineSoft)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides if (enabled) contentColor else contentColor.copy(alpha = 0.4f),
        ) {
            content()
        }
    }
}

private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

fun pitchName(pitch: Int): String {
    val name = NOTE_NAMES[pitch.mod(12)]
    val octave = pitch / 12 - 1
    return "$name$octave"
}
