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
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect

var isDarkThemeGlobal by mutableStateOf(false)

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

object WindScoreColors {
    val primary: Color get() = if (isDarkThemeGlobal) DARK_PRIMARY else LIGHT_PRIMARY
    val onPrimary: Color get() = if (isDarkThemeGlobal) DARK_ON_PRIMARY else LIGHT_ON_PRIMARY
    val ink: Color get() = if (isDarkThemeGlobal) DARK_INK else LIGHT_INK
    val canvas: Color get() = if (isDarkThemeGlobal) DARK_CANVAS else LIGHT_CANVAS
    val inverseCanvas: Color get() = if (isDarkThemeGlobal) LIGHT_CANVAS else DARK_CANVAS
    val inverseInk: Color get() = if (isDarkThemeGlobal) LIGHT_INK else DARK_INK
    val onInverseSoft: Color get() = if (isDarkThemeGlobal) LIGHT_CANVAS else DARK_CANVAS
    val hairline: Color get() = if (isDarkThemeGlobal) DARK_HAIRLINE else LIGHT_HAIRLINE
    val hairlineSoft: Color get() = if (isDarkThemeGlobal) DARK_HAIRLINE_SOFT else LIGHT_HAIRLINE_SOFT
    val surfaceSoft: Color get() = if (isDarkThemeGlobal) DARK_SURFACE_SOFT else LIGHT_SURFACE_SOFT

    val blockLime: Color get() = if (isDarkThemeGlobal) Color(0xFF4C523C) else Color(0xFFDCEEB1)
    val blockLilac: Color get() = if (isDarkThemeGlobal) Color(0xFF403657) else Color(0xFFC5B0F4)
    val blockCream: Color get() = if (isDarkThemeGlobal) Color(0xFF524D41) else Color(0xFFF4ECD6)
    val blockPink: Color get() = if (isDarkThemeGlobal) Color(0xFF5E4E4E) else Color(0xFFEFD4D4)
    val blockMint: Color get() = if (isDarkThemeGlobal) Color(0xFF3F4D42) else Color(0xFFC8E6CD)
    val blockCoral: Color get() = if (isDarkThemeGlobal) Color(0xFF57433B) else Color(0xFFF3C9B6)
    val blockNavy: Color get() = if (isDarkThemeGlobal) Color(0xFFDCDCEB) else Color(0xFF1F1D3D)

    val accentMagenta = Color(0xFFFF3D8B)
    val semanticSuccess = Color(0xFF1EA64A)
    val overlayScrim = Color(0xFF000000)

    // 准度计分语义色：干净（冷绿）/ 卡壳（暖琥珀，避免红色）。深色模式下提亮以保证对比度。
    val scoreClean: Color get() = if (isDarkThemeGlobal) Color(0xFF5BD68A) else Color(0xFF3DBB6C)
    val scoreStuck: Color get() = if (isDarkThemeGlobal) Color(0xFFF0B85A) else Color(0xFFE0A33A)
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
    SideEffect {
        isDarkThemeGlobal = darkTheme
    }
    val colorScheme = if (darkTheme) windScoreDarkColors else windScoreLightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = windScoreTypography,
        shapes = windScoreShapes,
        content = content,
    )
}
