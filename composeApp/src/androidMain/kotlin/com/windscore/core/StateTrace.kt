package com.windscore.core

import android.util.Log

internal actual fun traceState(message: String) {
    Log.d("WindScore-State", message)
}
