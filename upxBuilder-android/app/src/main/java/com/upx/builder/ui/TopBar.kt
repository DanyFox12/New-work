package com.upx.builder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.upx.builder.app.AppState
import com.upx.builder.i18n.StringKey
import com.upx.builder.project.BuildAction

/**
 * The action toolbar across the top — like Android Studio's main toolbar.
 *
 * On wide screens every action gets its own icon. On compact (phone-portrait)
 * screens only the essentials stay inline and the rest move into an overflow
 * menu, so every option is always reachable without rotating the device.
 */
@Composable
fun TopBar(
    state: AppState,
    compact: Boolean,
    onNewProject: () -> Unit,
    onOpenFolder: () -> Unit,
    onBuild: (BuildAction) -> Unit,
    onChooseTheme: () -> Unit,
    onChooseLanguage: () -> Unit,
    onGuide: () -> Unit,
    onSetup: () -> Unit,
) {
    val hasProject = state.project != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (!compact) {
            Text(
                text = state.tr(StringKey.APP_NAME),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.width(4.dp))
        }

        // Essentials — always inline.
        BarButton(Icons.Filled.CreateNewFolder, state.tr(StringKey.NEW_PROJECT), onNewProject)
        BarButton(Icons.Filled.Save, state.tr(StringKey.SAVE), { state.saveActive() }, enabled = state.activeFile != null)
        if (state.building) {
            BarButton(Icons.Filled.Stop, state.tr(StringKey.STOP), { state.stopBuild() })
        } else {
            BarButton(Icons.Filled.PlayArrow, state.tr(StringKey.RUN), { onBuild(BuildAction.RUN) }, enabled = hasProject)
        }
        BarButton(Icons.Filled.Build, state.tr(StringKey.BUILD), { onBuild(BuildAction.BUILD) }, enabled = hasProject && !state.building)
        BarButton(Icons.Filled.ViewSidebar, state.tr(StringKey.TOGGLE_PROJECT), { state.toggleProjectPanel() })

        Spacer(Modifier.weight(1f))

        if (compact) {
            // Everything else lives in the overflow menu on phones.
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                BarButton(Icons.Filled.MoreVert, state.tr(StringKey.MORE_ACTIONS), { menuOpen = true })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    OverflowItem(Icons.Filled.FolderOpen, state.tr(StringKey.OPEN_FOLDER)) { menuOpen = false; onOpenFolder() }
                    OverflowItem(Icons.Filled.CleaningServices, state.tr(StringKey.CLEAN), enabled = hasProject && !state.building) { menuOpen = false; onBuild(BuildAction.CLEAN) }
                    OverflowItem(Icons.Filled.Terminal, state.tr(StringKey.TOGGLE_CONSOLE)) { menuOpen = false; state.toggleConsole() }
                    OverflowItem(Icons.Filled.Palette, state.tr(StringKey.THEME)) { menuOpen = false; onChooseTheme() }
                    OverflowItem(Icons.Filled.Language, state.tr(StringKey.LANGUAGE)) { menuOpen = false; onChooseLanguage() }
                    OverflowItem(Icons.Filled.Download, state.tr(StringKey.SETUP)) { menuOpen = false; onSetup() }
                    OverflowItem(Icons.Filled.Help, state.tr(StringKey.DOCUMENTATION)) { menuOpen = false; onGuide() }
                }
            }
        } else {
            BarButton(Icons.Filled.FolderOpen, state.tr(StringKey.OPEN_FOLDER), onOpenFolder)
            BarButton(Icons.Filled.CleaningServices, state.tr(StringKey.CLEAN), { onBuild(BuildAction.CLEAN) }, enabled = hasProject && !state.building)
            BarButton(Icons.Filled.Terminal, state.tr(StringKey.TOGGLE_CONSOLE), { state.toggleConsole() })
            BarButton(Icons.Filled.Palette, state.tr(StringKey.THEME), onChooseTheme)
            BarButton(Icons.Filled.Language, state.tr(StringKey.LANGUAGE), onChooseLanguage)
            BarButton(Icons.Filled.Download, state.tr(StringKey.SETUP), onSetup)
            BarButton(Icons.Filled.Help, state.tr(StringKey.DOCUMENTATION), onGuide)
        }
    }
}

@Composable
private fun OverflowItem(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun BarButton(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        )
    }
}
