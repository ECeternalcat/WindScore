package com.windscore.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.windscore.ui.theme.WindScoreColors
import com.windscore.ui.theme.WindScoreRounded

enum class PillButtonStyle { PRIMARY, SECONDARY }

@Composable
fun PillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: PillButtonStyle = PillButtonStyle.PRIMARY,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable () -> Unit,
) {
    val (container, contentColor, disabledContainer, disabledContent) = when (style) {
        PillButtonStyle.PRIMARY -> Quad(
            WindScoreColors.primary,
            WindScoreColors.onPrimary,
            WindScoreColors.hairline,
            WindScoreColors.ink.copy(alpha = 0.4f),
        )
        PillButtonStyle.SECONDARY -> Quad(
            WindScoreColors.canvas,
            WindScoreColors.ink,
            WindScoreColors.hairlineSoft,
            WindScoreColors.ink.copy(alpha = 0.4f),
        )
    }
    val border = when (style) {
        PillButtonStyle.PRIMARY -> null
        PillButtonStyle.SECONDARY -> BorderStroke(1.dp, WindScoreColors.hairline)
    }
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        enabled = enabled,
        shape = RoundedCornerShape(WindScoreRounded.pill),
        border = border,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = contentColor,
            disabledContainerColor = disabledContainer,
            disabledContentColor = disabledContent,
        ),
        contentPadding = contentPadding,
    ) {
        content()
    }
}

private data class Quad(
    val container: androidx.compose.ui.graphics.Color,
    val content: androidx.compose.ui.graphics.Color,
    val disabledContainer: androidx.compose.ui.graphics.Color,
    val disabledContent: androidx.compose.ui.graphics.Color,
)
