package com.windscore.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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
private val LIGHT_PRIMARY = Color(0xFF000000)
private val LIGHT_ON_PRIMARY = Color(0xFFFFFFFF)
private val LIGHT_INK = Color(0xFF000000)
private val LIGHT_CANVAS = Color(0xFFFFFFFF)
private val LIGHT_SURFACE_SOFT = Color(0xFFF7F7F5)
private val LIGHT_HAIRLINE = Color(0xFFE6E6E6)
private val LIGHT_HAIRLINE_SOFT = Color(0xFFF1F1F1)

private val DARK_PRIMARY = Color(0xFFFFFFFF)
private val DARK_ON_PRIMARY = Color(0xFF000000)
private val DARK_INK = Color(0xFFFFFFFF)
private val DARK_CANVAS = Color(0xFF1E1E1E)
private val DARK_SURFACE_SOFT = Color(0xFF2D2D2D)
private val DARK_HAIRLINE = Color(0xFF2E2E2E)
private val DARK_HAIRLINE_SOFT = Color(0xFF242424)

@Immutable
data class WindScorePalette(
    val primary: Color,
    val onPrimary: Color,
    val ink: Color,
    val canvas: Color,
    val inverseCanvas: Color,
    val inverseInk: Color,
    val onInverseSoft: Color,
    val hairline: Color,
    val hairlineSoft: Color,
    val surfaceSoft: Color,
    val blockLime: Color,
    val blockLilac: Color,
    val blockCream: Color,
    val blockPink: Color,
    val blockMint: Color,
    val blockCoral: Color,
    val blockNavy: Color,
    val accentMagenta: Color,
    val semanticSuccess: Color,
    val overlayScrim: Color,
    val scoreClean: Color,
    val scoreStuck: Color,
    val leftNoteUnhit: Color,
    val rightNoteUnhit: Color,
)

private val LightWindScorePalette = WindScorePalette(
    primary = LIGHT_PRIMARY,
    onPrimary = LIGHT_ON_PRIMARY,
    ink = LIGHT_INK,
    canvas = LIGHT_CANVAS,
    inverseCanvas = DARK_CANVAS,
    inverseInk = DARK_INK,
    onInverseSoft = DARK_CANVAS,
    hairline = LIGHT_HAIRLINE,
    hairlineSoft = LIGHT_HAIRLINE_SOFT,
    surfaceSoft = LIGHT_SURFACE_SOFT,
    blockLime = Color(0xFFDCEEB1),
    blockLilac = Color(0xFFC5B0F4),
    blockCream = Color(0xFFF4ECD6),
    blockPink = Color(0xFFEFD4D4),
    blockMint = Color(0xFFC8E6CD),
    blockCoral = Color(0xFFF3C9B6),
    blockNavy = Color(0xFF1F1D3D),
    accentMagenta = Color(0xFFFF3D8B),
    semanticSuccess = Color(0xFF1EA64A),
    overlayScrim = Color(0xFF000000),
    scoreClean = Color(0xFF3DBB6C),
    scoreStuck = Color(0xFFE0A33A),
    leftNoteUnhit = Color(0xFF9DC9A4),
    rightNoteUnhit = Color(0xFFD49A7E),
)

private val DarkWindScorePalette = WindScorePalette(
    primary = DARK_PRIMARY,
    onPrimary = DARK_ON_PRIMARY,
    ink = DARK_INK,
    canvas = DARK_CANVAS,
    inverseCanvas = LIGHT_CANVAS,
    inverseInk = LIGHT_INK,
    onInverseSoft = LIGHT_CANVAS,
    hairline = DARK_HAIRLINE,
    hairlineSoft = DARK_HAIRLINE_SOFT,
    surfaceSoft = DARK_SURFACE_SOFT,
    blockLime = Color(0xFF4C523C),
    blockLilac = Color(0xFF403657),
    blockCream = Color(0xFF524D41),
    blockPink = Color(0xFF5E4E4E),
    blockMint = Color(0xFF3F4D42),
    blockCoral = Color(0xFF57433B),
    blockNavy = Color(0xFFDCDCEB),
    accentMagenta = Color(0xFFFF3D8B),
    semanticSuccess = Color(0xFF1EA64A),
    overlayScrim = Color(0xFF000000),
    scoreClean = Color(0xFF5BD68A),
    scoreStuck = Color(0xFFF0B85A),
    leftNoteUnhit = Color(0xFF5D7D62),
    rightNoteUnhit = Color(0xFF8C6654),
)

private val LocalWindScoreColors = staticCompositionLocalOf { LightWindScorePalette }

internal fun windScorePalette(darkTheme: Boolean): WindScorePalette =
    if (darkTheme) DarkWindScorePalette else LightWindScorePalette

@Composable
fun windScoreColors(): WindScorePalette = LocalWindScoreColors.current

/** Compatibility facade for composables; drawing code captures [windScoreColors] before Canvas. */
object WindScoreColors {
    val primary: Color @Composable get() = windScoreColors().primary
    val onPrimary: Color @Composable get() = windScoreColors().onPrimary
    val ink: Color @Composable get() = windScoreColors().ink
    val canvas: Color @Composable get() = windScoreColors().canvas
    val inverseCanvas: Color @Composable get() = windScoreColors().inverseCanvas
    val inverseInk: Color @Composable get() = windScoreColors().inverseInk
    val onInverseSoft: Color @Composable get() = windScoreColors().onInverseSoft
    val hairline: Color @Composable get() = windScoreColors().hairline
    val hairlineSoft: Color @Composable get() = windScoreColors().hairlineSoft
    val surfaceSoft: Color @Composable get() = windScoreColors().surfaceSoft
    val blockLime: Color @Composable get() = windScoreColors().blockLime
    val blockLilac: Color @Composable get() = windScoreColors().blockLilac
    val blockCream: Color @Composable get() = windScoreColors().blockCream
    val blockPink: Color @Composable get() = windScoreColors().blockPink
    val blockMint: Color @Composable get() = windScoreColors().blockMint
    val blockCoral: Color @Composable get() = windScoreColors().blockCoral
    val blockNavy: Color @Composable get() = windScoreColors().blockNavy
    val accentMagenta: Color @Composable get() = windScoreColors().accentMagenta
    val semanticSuccess: Color @Composable get() = windScoreColors().semanticSuccess
    val overlayScrim: Color @Composable get() = windScoreColors().overlayScrim
    val scoreClean: Color @Composable get() = windScoreColors().scoreClean
    val scoreStuck: Color @Composable get() = windScoreColors().scoreStuck
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
    primary = LIGHT_PRIMARY,
    onPrimary = LIGHT_ON_PRIMARY,
    secondary = LIGHT_CANVAS,
    onSecondary = LIGHT_INK,
    background = LIGHT_CANVAS,
    onBackground = LIGHT_INK,
    surface = LIGHT_CANVAS,
    onSurface = LIGHT_INK,
    surfaceVariant = LIGHT_SURFACE_SOFT,
    onSurfaceVariant = LIGHT_INK,
    outline = LIGHT_HAIRLINE,
)

private val windScoreDarkColors = darkColorScheme(
    primary = DARK_PRIMARY,
    onPrimary = DARK_ON_PRIMARY,
    secondary = DARK_CANVAS,
    onSecondary = DARK_INK,
    background = DARK_CANVAS,
    onBackground = DARK_INK,
    surface = DARK_CANVAS,
    onSurface = DARK_INK,
    surfaceVariant = DARK_SURFACE_SOFT,
    onSurfaceVariant = DARK_INK,
    outline = DARK_HAIRLINE,
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
    val palette = windScorePalette(darkTheme)
    val colorScheme = if (darkTheme) windScoreDarkColors else windScoreLightColors
    CompositionLocalProvider(LocalWindScoreColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = windScoreTypography,
            shapes = windScoreShapes,
            content = content,
        )
    }
}
