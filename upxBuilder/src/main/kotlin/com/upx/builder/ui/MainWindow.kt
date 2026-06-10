package com.upx.builder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.upx.builder.app.AppState
import com.upx.builder.editor.Language
import com.upx.builder.i18n.StringKey
import com.upx.builder.project.BuildAction

private enum class ActiveDialog { NONE, NEW_PROJECT, THEME, LANGUAGE, GUIDE }

@Composable
fun MainWindow(state: AppState) {
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf(ActiveDialog.NONE) }

    // Arabic mirrors the whole layout right-to-left.
    val direction = if (state.language.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    MaterialTheme(colorScheme = state.theme.colorScheme) {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // Compact = phone-portrait-ish widths. The layout adapts so every
                // option stays reachable without rotating the device.
                val compact = maxWidth < 600.dp
                val consoleHeight: Dp = if (maxHeight < 700.dp) 150.dp else 200.dp

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TopBar(
                            state = state,
                            compact = compact,
                            onNewProject = { dialog = ActiveDialog.NEW_PROJECT },
                            onOpenFolder = { /* hook for a native folder picker */ },
                            onBuild = { action -> state.runBuild(scope, action) },
                            onChooseTheme = { dialog = ActiveDialog.THEME },
                            onChooseLanguage = { dialog = ActiveDialog.LANGUAGE },
                            onGuide = { dialog = ActiveDialog.GUIDE },
                        )

                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            // On compact screens an empty project panel would hide the
                            // welcome screen, so it only appears once a project is open.
                            val showPanel = state.projectPanelVisible && (!compact || state.project != null)
                            if (showPanel) {
                                if (compact) {
                                    // On phones the project tree takes the whole content
                                    // area and closes itself when a file is opened.
                                    ProjectPanel(
                                        state = state,
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        onFileOpened = { state.hideProjectPanel() },
                                    )
                                } else {
                                    ProjectPanel(state, modifier = Modifier.width(240.dp).fillMaxHeight())
                                    Box(
                                        Modifier.width(1.dp).fillMaxHeight()
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                    )
                                }
                            }

                            if (!(compact && showPanel)) {
                                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    EditorAndConsole(
                                        state = state,
                                        consoleHeight = consoleHeight,
                                        onNewProject = { dialog = ActiveDialog.NEW_PROJECT },
                                    )
                                }
                            }
                        }

                        StatusBar(state)
                    }
                }

                when (dialog) {
                    ActiveDialog.NEW_PROJECT -> NewProjectDialog(state) { dialog = ActiveDialog.NONE }
                    ActiveDialog.THEME -> ThemeDialog(state) { dialog = ActiveDialog.NONE }
                    ActiveDialog.LANGUAGE -> LanguageDialog(state) { dialog = ActiveDialog.NONE }
                    ActiveDialog.GUIDE -> GuideDialog(state) { dialog = ActiveDialog.NONE }
                    ActiveDialog.NONE -> Unit
                }
            }
        }
    }
}

@Composable
private fun EditorAndConsole(state: AppState, consoleHeight: Dp, onNewProject: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val active = state.activeFile
            if (active == null) {
                if (state.project == null) {
                    WelcomeScreen(state, onNewProject = onNewProject)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            state.tr(StringKey.NO_FILE_OPEN),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    EditorTabs(state)
                    CodeEditor(
                        text = active.content,
                        language = Language.fromFileName(active.name),
                        theme = state.theme,
                        onTextChange = { state.updateActiveContent(it) },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }

        if (state.consoleVisible) {
            Box(
                Modifier.height(1.dp).fillMaxWidth()
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            )
            BottomPanel(state, modifier = Modifier.height(consoleHeight).fillMaxWidth())
        }
    }
}
