package com.windscore

import androidx.compose.runtime.Composable

/**
 * 桌面端无系统返回手势：[PlatformBackHandler] 为空实现，
 * [exitApplication] / [showPlatformToast] 不会被调用（桌面通过窗口关闭按钮退出）。
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // no-op on desktop
}

actual fun exitApplication() {
    // no-op on desktop
}

actual fun showPlatformToast(message: String) {
    println("[Toast] $message")
}
