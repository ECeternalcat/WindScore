package com.windscore.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun Modifier.scrollWheelHandler(onScroll: (Float) -> Unit): Modifier =
    this.onPointerEvent(PointerEventType.Scroll) { event ->
        event.changes.firstOrNull()?.let { c ->
            if (c.scrollDelta.y != 0f) onScroll(c.scrollDelta.y)
        }
    }
