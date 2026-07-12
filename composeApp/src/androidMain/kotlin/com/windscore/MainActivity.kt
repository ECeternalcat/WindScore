package com.windscore

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.windscore.midi.createMidiManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        // 练习时保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            App(createMidiManager(), isCompact = true)
        }
    }
}
