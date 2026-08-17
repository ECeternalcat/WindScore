package com.windscore.core

import android.util.Log

internal actual fun traceState(message: String) {
    // Host unit tests ship a stub android.util.Log that throws; keep tests green.
    runCatching { Log.d("WindScore-State", message) }
}
