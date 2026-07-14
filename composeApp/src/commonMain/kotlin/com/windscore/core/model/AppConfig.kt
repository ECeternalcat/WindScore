package com.windscore.core.model

import com.windscore.ViewMode
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode { FOLLOW_SYSTEM, LIGHT, DARK }

@Serializable
data class AppConfig(
    val themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    val defaultViewMode: ViewMode = ViewMode.SHEET_FULL,
    val defaultCountdownSeconds: Int = 3,
)
