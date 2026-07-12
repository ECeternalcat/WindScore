package com.windscore.ui

import androidx.compose.ui.Modifier

internal actual fun Modifier.scrollWheelHandler(onScroll: (Float) -> Unit): Modifier = this
