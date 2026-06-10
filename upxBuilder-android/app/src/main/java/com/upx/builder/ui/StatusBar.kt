package com.upx.builder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.upx.builder.app.AppState
import com.upx.builder.editor.Language
import com.upx.builder.i18n.StringKey

@Composable
fun StatusBar(state: AppState) {
    val active = state.activeFile
    val language = active?.let { Language.fromFileName(it.name) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = if (state.building) "${state.tr(StringKey.BUILD_STARTED)}…" else state.tr(StringKey.READY),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
        Spacer(Modifier.weight(1f))
        if (language != null) {
            Text(
                text = language.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        }
        Text(
            text = "${state.theme.displayName} · ${state.language.displayName}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}
