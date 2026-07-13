package com.windscore.ui

import android.content.Intent
import android.net.Uri

actual fun openUrl(url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        android.app.Application().applicationContext.startActivity(intent)
    } catch (e: Exception) {
        println("[URL] Failed to open: $url - ${e.message}")
    }
}
