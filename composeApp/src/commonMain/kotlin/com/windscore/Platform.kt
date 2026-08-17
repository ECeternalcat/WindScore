package com.windscore

import androidx.compose.runtime.Composable

/**
 * 平台返回键/手势拦截。Android 上接系统 Back（手势 + 按钮）；桌面端为空实现（无返回手势）。
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** 退出应用。Android 结束 Activity；桌面端无操作（由窗口关闭按钮退出）。 */
expect fun exitApplication()

/** 平台原生 Toast（用于全局提示，如"再次返回退出"）。桌面端无操作。 */
expect fun showPlatformToast(message: String)
