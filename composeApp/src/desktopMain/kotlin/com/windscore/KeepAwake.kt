package com.windscore

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform

private interface ExecutionStateApi : Library {
    fun SetThreadExecutionState(flags: Int): Int
}

private const val ES_CONTINUOUS = -0x80000000
private const val ES_SYSTEM_REQUIRED = 0x00000001
private const val ES_DISPLAY_REQUIRED = 0x00000002

internal fun setKeepAwake(enabled: Boolean) {
    if (!Platform.isWindows()) return
    runCatching {
        val kernel32 = Native.load("kernel32", ExecutionStateApi::class.java)
        val flags = if (enabled) ES_CONTINUOUS or ES_SYSTEM_REQUIRED or ES_DISPLAY_REQUIRED else ES_CONTINUOUS
        kernel32.SetThreadExecutionState(flags)
    }.onFailure { println("[KeepAwake] ${it.message}") }
}
