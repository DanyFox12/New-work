package com.upx.builder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.upx.builder.app.AppState
import com.upx.builder.i18n.StringKey
import com.upx.builder.project.BuildAction

/** The action toolbar across the top — like Android Studio's main toolbar. */
@Composable
fun TopBar(
    state: AppState,
    onNewProject: () -> Unit,
    onOpenFolder: () -> Unit,
    onBuild: (BuildAction) -> Unit,
    onChooseTheme: () -> Unit,
    onChooseLanguage: () -> Unit,
    onGuide: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = state.tr(StringKey.APP_NAME),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.width(8.dp))

        BarButton(Icons.Filled.CreateNewFolder, state.tr(StringKey.NEW_PROJECT), onNewProject)
        BarButton(Icons.Filled.FolderOpen, state.tr(StringKey.OPEN_FOLDER), onOpenFolder)
        BarButton(Icons.Filled.Save, state.tr(StringKey.SAVE), { state.saveActive() }, enabled = state.activeFile != null)

        Spacer(Modifier.width(8.dp))

        val hasProject = state.project != null
        if (state.building) {
            BarButton(Icons.Filled.Stop, state.tr(StringKey.STOP), { state.stopBuild() })
        } else {
            BarButton(Icons.Filled.PlayArrow, state.tr(StringKey.RUN), { onBuild(BuildAction.RUN) }, enabled = hasProject)
        }
        BarButton(Icons.Filled.Build, state.tr(StringKey.BUILD), { onBuild(BuildAction.BUILD) }, enabled = hasProject && !state.building)
        BarButton(Icons.Filled.CleaningServices, state.tr(StringKey.CLEAN), { onBuild(BuildAction.CLEAN) }, enabled = hasProject && !state.building)

        Spacer(Modifier.weight(1f))

        BarButton(Icons.Filled.ViewSidebar, state.tr(StringKey.TOGGLE_PROJECT), { state.toggleProjectPanel() })
        BarButton(Icons.Filled.Terminal, state.tr(StringKey.TOGGLE_CONSOLE), { state.toggleConsole() })
        BarButton(Icons.Filled.Palette, state.tr(StringKey.THEME), onChooseTheme)
        BarButton(Icons.Filled.Language, state.tr(StringKey.LANGUAGE), onChooseLanguage)
        BarButton(Icons.Filled.Help, state.tr(StringKey.DOCUMENTATION), onGuide)
    }
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
