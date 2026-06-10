package com.upx.builder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.upx.builder.app.AppState
import com.upx.builder.i18n.StringKey
import com.upx.builder.project.FileNode

@Composable
fun ProjectPanel(
    state: AppState,
    modifier: Modifier = Modifier,
    onFileOpened: () -> Unit = {},
) {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .fillMaxSize(),
    ) {
        Text(
            text = state.tr(StringKey.PROJECT_EXPLORER).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(12.dp),
        )
        val project = state.project
        if (project == null) {
            Text(
                text = state.tr(StringKey.NO_RECENT),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        } else {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                FileTreeNode(FileNode.of(project.root), 0, expanded, state, onFileOpened)
            }
        }
    }
}

@Composable
private fun FileTreeNode(
    node: FileNode,
    depth: Int,
    expanded: MutableMap<String, Boolean>,
    state: AppState,
    onFileOpened: () -> Unit,
) {
    val path = node.file.absolutePath
    val isOpen = expanded[path] ?: (depth == 0)
    val icon: ImageVector = when {
        node.isDirectory && isOpen -> Icons.Filled.FolderOpen
        node.isDirectory -> Icons.Filled.Folder
        else -> Icons.Filled.Description
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (node.isDirectory) {
                    expanded[path] = !isOpen
                } else {
                    state.openFile(node.file)
                    onFileOpened()
                }
            }
            .padding(start = (8 + depth * 14).dp, top = 3.dp, bottom = 3.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (node.isDirectory) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.width(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = node.name,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (node.isDirectory && isOpen) {
        node.children().forEach { child ->
            FileTreeNode(child, depth + 1, expanded, state, onFileOpened)
        }
    }
}
