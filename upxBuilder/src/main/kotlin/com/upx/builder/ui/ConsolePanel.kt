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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
            Spacer(Modifier.weight(1f))
            if (state.bottomTab == BottomTab.CONSOLE) {
                IconButton(onClick = { state.clearConsole() }) {
                    Icon(Icons.Filled.ClearAll, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        when (state.bottomTab) {
            BottomTab.CONSOLE -> ConsoleList(state)
            BottomTab.PROBLEMS -> ProblemsList(state)
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
