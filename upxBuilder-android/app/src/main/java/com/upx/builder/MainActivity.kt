package com.upx.builder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.upx.builder.app.AppState
import com.upx.builder.ui.MainWindow

/**
 * upxBuilder for Android — a beautiful, multi-language mobile IDE for editing
 * Flutter (Dart), C++, Java and Kotlin, with the same Android-Studio-style
 * design as the desktop edition. Built with Kotlin and Jetpack Compose.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val state = AppState(applicationContext)
        setContent {
            MainWindow(state)
        }
    }
}
