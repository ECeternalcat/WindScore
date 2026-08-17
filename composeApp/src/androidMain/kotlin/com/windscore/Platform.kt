package com.windscore

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable

/** 持有当前 Activity 的弱引用，供 [exitApplication] / [showPlatformToast] 使用。 */
internal object ActivityRef {
    @Volatile
    var current: Activity? = null
}

fun initializeAndroidPlatform(activity: Activity) {
    com.windscore.core.AndroidContext.context = activity.applicationContext
    ActivityRef.current = activity
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}

actual fun exitApplication() {
    ActivityRef.current?.finishAffinity()
}

actual fun showPlatformToast(message: String) {
    val ctx = ActivityRef.current ?: com.windscore.core.requireAndroidContext()
    Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
}
