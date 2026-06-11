package com.upx.builder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.upx.builder.app.AppState
import com.upx.builder.app.BottomTab
import com.upx.builder.editor.DiagnosticSeverity
import com.upx.builder.i18n.StringKey

/** Bottom panel hosting the Console and Problems tabs, like Android Studio. */
@Composable
fun BottomPanel(state: AppState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface).fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PanelTab(
                label = state.tr(StringKey.CONSOLE),
                selected = state.bottomTab == BottomTab.CONSOLE,
                onClick = { state.selectBottomTab(BottomTab.CONSOLE) },
            )
            Spacer(Modifier.width(4.dp))
            val problemCount = state.diagnostics.size
            PanelTab(
                label = state.tr(StringKey.PROBLEMS) + if (problemCount > 0) " ($problemCount)" else "",
                selected = state.bottomTab == BottomTab.PROBLEMS,
                onClick = { state.selectBottomTab(BottomTab.PROBLEMS) },
            )
            Spacer(Modifier.width(4.dp))
            PanelTab(
                label = state.tr(StringKey.TERMINAL),
                selected = state.bottomTab == BottomTab.TERMINAL,
                onClick = { state.selectBottomTab(BottomTab.TERMINAL) },
            )
            Spacer(Modifier.weight(1f))
            if (state.bottomTab == BottomTab.CONSOLE) {
                IconButton(onClick = { state.clearConsole() }) {
                    Icon(Icons.Filled.ClearAll, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (state.bottomTab == BottomTab.TERMINAL) {
                IconButton(onClick = { state.clearTerminal() }) {
                    Icon(Icons.Filled.ClearAll, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            // Termux-style: expand the panel to cover the whole screen and back.
            IconButton(onClick = { state.toggleBottomPanelExpanded() }) {
                Icon(
                    if (state.bottomPanelExpanded) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        when (state.bottomTab) {
            BottomTab.CONSOLE -> ConsoleList(state)
            BottomTab.PROBLEMS -> ProblemsList(state)
            BottomTab.TERMINAL -> TerminalPanel(state)
        }
    }
}

@Composable
private fun PanelTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun ConsoleList(state: AppState) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.consoleOutput.size) {
        if (state.consoleOutput.isNotEmpty()) {
            listState.animateScrollToItem(state.consoleOutput.lastIndex)
        }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        items(state.consoleOutput) { line ->
            Text(
                text = line.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = if (line.isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun ProblemsList(state: AppState) {
    if (state.diagnostics.isEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF5FB865),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = state.tr(StringKey.NO_PROBLEMS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        items(state.diagnostics) { d ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val (icon, tint) = when (d.severity) {
                    DiagnosticSeverity.ERROR -> Icons.Filled.Error to MaterialTheme.colorScheme.error
                    DiagnosticSeverity.WARNING -> Icons.Filled.Warning to Color(0xFFD9A343)
                    DiagnosticSeverity.INFO -> Icons.Filled.Info to MaterialTheme.colorScheme.primary
                }
                Icon(icon, contentDescription = d.severity.name, tint = tint, modifier = Modifier.size(14.dp))
                Text(
                    text = "${state.tr(StringKey.LINE)} ${d.line}: ${d.message}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                )
            }
        }
    }
}

// Termux-style palette: a real terminal is always dark, whatever the app theme.
private val TermBackground = Color(0xFF0B0F14)
private val TermInputBar = Color(0xFF151B22)
private val TermText = Color(0xFFE6EDF3)
private val TermError = Color(0xFFFF6B6B)
private val TermGreen = Color(0xFF3DDC84)
private val TermDim = Color(0xFF7D8590)

@Composable
private fun TerminalPanel(state: AppState) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.terminalOutput.size) {
        if (state.terminalOutput.isNotEmpty()) {
            listState.animateScrollToItem(state.terminalOutput.lastIndex)
        }
    }
    Column(modifier = Modifier.fillMaxSize().background(TermBackground)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
        ) {
            items(state.terminalOutput) { line ->
                Text(
                    text = line.text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = if (line.isError) TermError else TermText,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TermInputBar)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.terminalPrompt + " $",
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = TermGreen,
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = input,
                onValueChange = { typed ->
                    // Enter submits the command (works for both hardware and IME enter).
                    if (typed.endsWith("\n")) {
                        val cmd = typed.trimEnd('\n')
                        input = ""
                        state.runTerminal(scope, cmd)
                    } else {
                        input = typed
                    }
                },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = TermText,
                ),
                cursorBrush = SolidColor(TermGreen),
                singleLine = false,
                maxLines = 1,
                decorationBox = { inner ->
                    Box {
                        if (input.isEmpty()) {
                            Text(
                                text = state.tr(StringKey.TERMINAL_PLACEHOLDER),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = TermDim,
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                            val cmd = input
                            input = ""
                            state.runTerminal(scope, cmd)
                            true
                        } else false
                    },
            )
            IconButton(onClick = {
                val cmd = input
                input = ""
                state.runTerminal(scope, cmd)
            }) {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = "Run",
                    tint = TermGreen,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
