package com.windscore.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 桌面 / Android 共享的墙钟时间工具（commonMain 通过 expect/actual 访问）。
 */
internal object DateTimeUtil {
    fun nowEpochMillis(): Long = System.currentTimeMillis()

    fun formatDateTime(epochMillis: Long): String = runCatching {
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
    }.getOrDefault("")
}
