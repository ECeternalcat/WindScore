package com.windscore.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * WindScore 设计系统（基于 Document/DESIGN.md 的 Figma 营销页风格适配）。
 *
 * 核心特征：
 * - 黑白编辑式基调：黑（primary/ink）+ 白（canvas）承载所有 Chrome。
 * - 超大 pastel 色块（block-lime / lilac / cream / mint / pink / coral / navy）作为功能分区。
 * - 所有 CTA 为胶囊形（pill）；图标按钮为圆形（full）。
 * - 大字号、负字距的展示标题；正文靠字重而非灰度区分层次。
 *
 * 字体：figmaSans/figmaMono 为专有字体，此处使用系统默认无衬线 + JetBrains Mono 风格回退，
 * 并按 DESIGN.md 的尺寸/字重/字距精确设置 Typography。
 */
object WindScoreColors {
    val primary = Color(0xFF000000)
    val onPrimary = Color(0xFFFFFFFF)
    val ink = Color(0xFF000000)
    val canvas = Color(0xFFFFFFFF)
    val inverseCanvas = Color(0xFF000000)
    val inverseInk = Color(0xFFFFFFFF)
    val onInverseSoft = Color(0xFFFFFFFF)
    val hairline = Color(0xFFE6E6E6)
    val hairlineSoft = Color(0xFFF1F1F1)
    val surfaceSoft = Color(0xFFF7F7F5)

    val blockLime = Color(0xFFDCEEB1)
    val blockLilac = Color(0xFFC5B0F4)
    val blockCream = Color(0xFFF4ECD6)
    val blockPink = Color(0xFFEFD4D4)
    val blockMint = Color(0xFFC8E6CD)
    val blockCoral = Color(0xFFF3C9B6)
    val blockNavy = Color(0xFF1F1D3D)

    val accentMagenta = Color(0xFFFF3D8B)
    val semanticSuccess = Color(0xFF1EA64A)
    val overlayScrim = Color(0xFF000000)
}

object WindScoreSpacing {
    val hair = 1.dp
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val section = 96.dp
}

object WindScoreRounded {
    val xs = 2.dp
    val sm = 6.dp
    val md = 8.dp
    val lg = 24.dp
    val xl = 32.dp
    val pill = 50.dp
    val full = 9999.dp
}

private val sansFamily = FontFamily.SansSerif
private val monoFamily = FontFamily.Monospace

private val windScoreTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = sansFamily,
        fontSize = 86.sp,
        fontWeight = FontWeight.W300,
        lineHeight = 86.sp,
        letterSpacing = (-1.72).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = sansFamily,
        fontSize = 64.sp,
        fontWeight = FontWeight.W300,
        lineHeight = (64 * 1.1).sp,
        letterSpacing = (-0.96).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = sansFamily,
        fontSize = 26.sp,
        fontWeight = FontWeight.W600,
        lineHeight = (26 * 1.35).sp,
        letterSpacing = (-0.26).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = sansFamily,
        fontSize = 26.sp,
        fontWeight = FontWeight.W300,
        lineHeight = (26 * 1.35).sp,
        letterSpacing = (-0.26).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = sansFamily,
        fontSize = 24.sp,
        fontWeight = FontWeight.W700,
        lineHeight = (24 * 1.45).sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = sansFamily,
        fontSize = 20.sp,
        fontWeight = FontWeight.W400,
        lineHeight = (20 * 1.4).sp,
        letterSpacing = (-0.14).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = sansFamily,
        fontSize = 20.sp,
        fontWeight = FontWeight.W300,
        lineHeight = (20 * 1.4).sp,
        letterSpacing = (-0.14).sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = sansFamily,
        fontSize = 18.sp,
        fontWeight = FontWeight.W300,
        lineHeight = (18 * 1.45).sp,
        letterSpacing = (-0.26).sp,
    ),
    bodySmall = TextStyle(
        fontFamily = sansFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.W300,
        lineHeight = (16 * 1.45).sp,
        letterSpacing = (-0.14).sp,
    ),
    labelLarge = TextStyle(
        fontFamily = sansFamily,
        fontSize = 20.sp,
        fontWeight = FontWeight.W500,
        lineHeight = (20 * 1.4).sp,
        letterSpacing = (-0.10).sp,
    ),
    labelMedium = TextStyle(
        fontFamily = monoFamily,
        fontSize = 18.sp,
        fontWeight = FontWeight.W400,
        lineHeight = (18 * 1.3).sp,
        letterSpacing = 0.54.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = monoFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.W400,
        lineHeight = 12.sp,
        letterSpacing = 0.6.sp,
    ),
)

private val windScoreLightColors = lightColorScheme(
    primary = WindScoreColors.primary,
    onPrimary = WindScoreColors.onPrimary,
    secondary = WindScoreColors.canvas,
    onSecondary = WindScoreColors.ink,
    background = WindScoreColors.canvas,
    onBackground = WindScoreColors.ink,
    surface = WindScoreColors.canvas,
    onSurface = WindScoreColors.ink,
    surfaceVariant = WindScoreColors.surfaceSoft,
    onSurfaceVariant = WindScoreColors.ink,
    outline = WindScoreColors.hairline,
)

private val windScoreDarkColors = darkColorScheme(
    primary = WindScoreColors.onPrimary,
    onPrimary = WindScoreColors.primary,
    secondary = WindScoreColors.inverseCanvas,
    onSecondary = WindScoreColors.inverseInk,
    background = WindScoreColors.inverseCanvas,
    onBackground = WindScoreColors.inverseInk,
    surface = WindScoreColors.inverseCanvas,
    onSurface = WindScoreColors.inverseInk,
    surfaceVariant = WindScoreColors.blockNavy,
    onSurfaceVariant = WindScoreColors.inverseInk,
    outline = WindScoreColors.onInverseSoft.copy(alpha = 0.3f),
)

val windScoreShapes = Shapes(
    extraSmall = RoundedCornerShape(WindScoreRounded.xs),
    small = RoundedCornerShape(WindScoreRounded.sm),
    medium = RoundedCornerShape(WindScoreRounded.md),
    large = RoundedCornerShape(WindScoreRounded.lg),
    extraLarge = RoundedCornerShape(WindScoreRounded.xl),
)

@Composable
fun WindScoreTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) windScoreDarkColors else windScoreLightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = windScoreTypography,
        shapes = windScoreShapes,
        content = content,
    )
}
