package com.upx.builder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.upx.builder.app.AppState

@Composable
fun EditorTabs(state: AppState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.openFiles.forEachIndexed { index, file ->
            val isActive = index == state.activeFileIndex
            Row(
                modifier = Modifier
                    .height(38.dp)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.background
                        else MaterialTheme.colorScheme.surface
                    )
                    .clickable { state.selectTab(index) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (file.dirty) {
                    Icon(
                        Icons.Filled.Circle,
                        contentDescription = "unsaved",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(8.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { state.closeTab(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "close",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}
