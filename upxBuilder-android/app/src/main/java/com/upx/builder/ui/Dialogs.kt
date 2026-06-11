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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.upx.builder.project.ToolCategory
import com.upx.builder.project.Toolchain
import com.upx.builder.project.Toolchains
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

/**
 * First-launch offer: download the development environment automatically.
 * "Essentials" sets up the Linux environment + BusyBox (~6 MB); "Everything"
 * adds the core dev set (compilers, Python, Java, Node, Go, Git). Shown once —
 * answering (or dismissing) never asks again; Setup stays in the toolbar.
 */
@Composable
fun SetupPromptDialog(state: AppState) {
    AlertDialog(
        onDismissRequest = { state.dismissSetupPrompt() },
        title = { Text(state.tr(StringKey.SETUP_PROMPT_TITLE)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = state.tr(StringKey.SETUP_PROMPT_BODY),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { state.acceptSetupPrompt(everything = false) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(state.tr(StringKey.INSTALL_ESSENTIALS)) }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { state.acceptSetupPrompt(everything = true) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(state.tr(StringKey.INSTALL_EVERYTHING)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { state.dismissSetupPrompt() }) { Text(state.tr(StringKey.LATER)) }
        },
    )
}

/**
 * The Setup guide: a one-tap installer for every developer toolchain — the
 * Android SDK, JDK/Java, platform-tools (adb), CMake, Python, Flutter/Dart,
 * Node, Go, Git and the C/C++ compilers. Each card shows what it provides, the
 * exact `pkg install …` command, and an Install button that runs it in the
 * Terminal. Mirrors the in-terminal `pkg` manager, sharing the [Toolchains]
 * catalogue so the two never disagree.
 */
@Composable
fun SetupGuideDialog(state: AppState, onDismiss: () -> Unit) {
    val categories = listOf(
        ToolCategory.CORE to StringKey.CAT_CORE,
        ToolCategory.LANGUAGE to StringKey.CAT_LANGUAGE,
        ToolCategory.BUILD to StringKey.CAT_BUILD,
        ToolCategory.ANDROID to StringKey.CAT_ANDROID,
        ToolCategory.UTIL to StringKey.CAT_UTIL,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.tr(StringKey.SETUP_TITLE)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = state.tr(StringKey.SETUP_INTRO),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { state.installTool("all"); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(state.tr(StringKey.INSTALL_CORE)) }
                Spacer(Modifier.height(12.dp))

                categories.forEach { (category, labelKey) ->
                    val tools = Toolchains.byCategory(category)
                    if (tools.isEmpty()) return@forEach
                    Text(
                        text = state.tr(labelKey),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                    tools.forEach { tool ->
                        ToolCard(state, tool) { id -> state.installTool(id); onDismiss() }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(state.tr(StringKey.CLOSE)) } },
    )
}

@Composable
private fun ToolCard(state: AppState, tool: Toolchain, onInstall: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(tool.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        tool.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onInstall(tool.id) }) { Text(state.tr(StringKey.INSTALL)) }
            }
            Spacer(Modifier.height(6.dp))
            val sizeSuffix = if (tool.sizeHint.isNotEmpty()) "  •  ${tool.sizeHint}" else ""
            Text(
                text = "${state.tr(StringKey.PROVIDES)}: ${tool.provides}$sizeSuffix",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Text(
                text = "$ ${tool.command}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
            if (tool.note.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = tool.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
        }
    }
}

/**
 * A simple folder browser: navigate with taps (".." goes up), then Open makes
 * the current folder the active project. Starts in the app's projects home,
 * where projects created by upxBuilder live.
 */
@Composable
fun OpenFolderDialog(state: AppState, onDismiss: () -> Unit) {
    var current by remember { mutableStateOf(state.projectsDir) }
    val children = remember(current) {
        current.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.tr(StringKey.OPEN_FOLDER)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = current.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    val parent = current.parentFile
                    if (parent != null && parent.canRead()) {
                        item {
                            FolderRow(name = "..", onClick = { current = parent })
                        }
                    }
                    items(children) { dir ->
                        FolderRow(name = dir.name, onClick = { current = dir })
                    }
                    if (children.isEmpty()) {
                        item {
                            Text(
                                text = state.tr(StringKey.NO_RECENT),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                state.openProject(current)
                onDismiss()
            }) { Text(state.tr(StringKey.OPEN)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(state.tr(StringKey.CANCEL)) }
        },
    )
}

@Composable
private fun FolderRow(name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium)
    }
}
