package com.upx.builder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.upx.builder.app.AppState
import com.upx.builder.i18n.StringKey

@Composable
fun ConsolePanel(state: AppState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.consoleOutput.size) {
        if (state.consoleOutput.isNotEmpty()) {
            listState.animateScrollToItem(state.consoleOutput.lastIndex)
        }
    }
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface).fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = state.tr(StringKey.CONSOLE).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            IconButton(onClick = { state.clearConsole() }) {
                Icon(Icons.Filled.ClearAll, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurface)
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
}
