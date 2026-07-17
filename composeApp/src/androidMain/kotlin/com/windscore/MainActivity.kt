package com.windscore

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.windscore.midi.createMidiManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.windscore.core.AndroidContext.context = applicationContext
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        // 练习时保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        // 全屏沉浸：隐藏状态栏 + 导航栏（瞬态，从边缘下滑/滑动可临时显示）
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            // 0 == WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_SYSTEM_BARS
            // （某些 core 版本未暴露该常量，直接用其字面值）
            systemBarsBehavior = 0
        }
        setContent {
            App(createMidiManager(), isCompact = true)
        }
    }
}
