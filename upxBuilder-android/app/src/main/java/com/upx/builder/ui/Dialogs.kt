@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.upx.builder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.upx.builder.app.AppState
import com.upx.builder.i18n.AppLanguage
import com.upx.builder.i18n.StringKey
import com.upx.builder.project.ProjectTemplate
import com.upx.builder.project.Templates
import com.upx.builder.theme.Themes
import java.io.File

@Composable
fun NewProjectDialog(state: AppState, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(Templates.all.first()) }
    var name by remember { mutableStateOf("MyApp") }
    var location by remember { mutableStateOf(state.projectsDir.absolutePath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.tr(StringKey.CREATE_NEW)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Templates.all.forEach { template ->
                        TemplateChip(
                            selected = template == selected,
                            label = templateLabel(state, template),
                            onClick = { selected = template },
                        )
                    }
                } // FlowRow
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(state.tr(StringKey.PROJECT_NAME)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(state.tr(StringKey.LOCATION)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = selected.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = selected.guide,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    val parent = File(location).apply { mkdirs() }
                    val root = selected.create(parent, name.trim())
                    state.openProject(root)
                    onDismiss()
                },
            ) { Text(state.tr(StringKey.CREATE)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(state.tr(StringKey.CANCEL)) }
        },
    )
}

private fun templateLabel(state: AppState, template: ProjectTemplate): String = when (template.id) {
    "flutter" -> state.tr(StringKey.TEMPLATE_FLUTTER)
    "cpp" -> state.tr(StringKey.TEMPLATE_CPP)
    "java" -> state.tr(StringKey.TEMPLATE_JAVA)
    "kotlin" -> state.tr(StringKey.TEMPLATE_KOTLIN)
    else -> template.displayName
}

@Composable
private fun TemplateChip(selected: Boolean, label: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.width(116.dp).height(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun ThemeDialog(state: AppState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.tr(StringKey.CHOOSE_THEME)) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(Themes.all) { theme ->
                    val isCurrent = theme.id == state.theme.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                            )
                            .clickable { state.applyTheme(theme) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Swatches preview
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            listOf(
                                theme.editorBackground, theme.syntax.keyword,
                                theme.syntax.string, theme.syntax.type, theme.accent,
                            ).forEach { c ->
                                Box(
                                    Modifier.size(16.dp).clip(CircleShape).background(c)
                                        .border(1.dp, Color.Black.copy(alpha = 0.15f), CircleShape)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(theme.displayName, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (theme.isDark) "dark" else "light",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(state.tr(StringKey.CLOSE)) } },
    )
}

@Composable
fun LanguageDialog(state: AppState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.tr(StringKey.CHOOSE_LANGUAGE)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                AppLanguage.entries.forEach { lang ->
                    val isCurrent = lang == state.language
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                            )
                            .clickable { state.applyLanguage(lang) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(lang.displayName, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "(${lang.code})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(state.tr(StringKey.CLOSE)) } },
    )
}

@Composable
fun GuideDialog(state: AppState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.tr(StringKey.GUIDE_TITLE)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Templates.all.forEach { template ->
                    Text(
                        text = template.language.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = template.guide,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(state.tr(StringKey.CLOSE)) } },
    )
}
