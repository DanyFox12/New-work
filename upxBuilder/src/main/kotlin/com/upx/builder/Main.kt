package com.upx.builder

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.upx.builder.app.AppState
import com.upx.builder.ui.MainWindow

/**
 * upxBuilder — a beautiful, multi-language IDE for Flutter, C++, Java and Kotlin.
 *
 * Built with Kotlin and Compose Multiplatform for Desktop (the same JVM lineage
 * as Android Studio / IntelliJ).
 */
fun main() = application {
    val windowState = rememberWindowState(
        size = DpSize(1280.dp, 820.dp),
        position = WindowPosition(androidx.compose.ui.Alignment.Center),
    )
    val state = remember { AppState() }
    Window(
        onCloseRequest = ::exitApplication,
        title = "upxBuilder",
        state = windowState,
    ) {
        MainWindow(state)
    }
}
