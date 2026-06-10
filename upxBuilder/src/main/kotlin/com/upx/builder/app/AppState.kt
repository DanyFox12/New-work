package com.upx.builder.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.upx.builder.editor.Diagnostic
import com.upx.builder.editor.DiagnosticsEngine
import com.upx.builder.editor.Language
import com.upx.builder.i18n.AppLanguage
import com.upx.builder.i18n.StringKey
import com.upx.builder.i18n.Strings
import com.upx.builder.project.BuildAction
import com.upx.builder.project.BuildLine
import com.upx.builder.project.BuildRunner
import com.upx.builder.project.OpenFile
import com.upx.builder.project.Project
import com.upx.builder.theme.AppTheme
import com.upx.builder.theme.Themes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.prefs.Preferences

/** Which tab the bottom panel is showing. */
enum class BottomTab { CONSOLE, PROBLEMS, TERMINAL }

/**
 * Single source of truth for the running application: the active theme, UI
 * language, open project, editor tabs and console output. UI components read
 * from here and call its methods; Compose recomposes on change.
 *
 * Theme and language choices persist across runs via [Preferences].
 */
class AppState {
    private val prefs = Preferences.userRoot().node("com/upx/builder")

    /** Default home for new projects. */
    val projectsDir: File = File(System.getProperty("user.home"), "upxBuilderProjects")
        .apply { mkdirs() }

    var theme by mutableStateOf(Themes.byId(prefs.get("theme", Themes.default.id)))
        private set

    var language by mutableStateOf(AppLanguage.fromCode(prefs.get("language", "en")))
        private set

    var project by mutableStateOf<Project?>(null)
        private set

    val openFiles = mutableStateListOf<OpenFile>()

    var activeFileIndex by mutableStateOf(-1)
        private set

    val consoleOutput = mutableStateListOf<BuildLine>()

    /** Live problems found in the active file by the diagnostics engine. */
    val diagnostics = mutableStateListOf<Diagnostic>()

    /** Output lines of the in-app terminal. */
    val terminalOutput = mutableStateListOf<BuildLine>()

    /** Terminal working directory; `cd` changes it between commands. */
    var terminalCwd by mutableStateOf<File?>(null)

    var bottomTab by mutableStateOf(BottomTab.CONSOLE)
        private set

    var consoleVisible by mutableStateOf(true)
        private set

    var projectPanelVisible by mutableStateOf(true)
        private set

    var building by mutableStateOf(false)
        private set

    private val buildRunner = BuildRunner()

    val activeFile: OpenFile? get() = openFiles.getOrNull(activeFileIndex)

    fun tr(key: StringKey): String = Strings.get(language, key)

    fun applyTheme(newTheme: AppTheme) {
        theme = newTheme
        prefs.put("theme", newTheme.id)
    }

    fun applyLanguage(newLanguage: AppLanguage) {
        language = newLanguage
        prefs.put("language", newLanguage.code)
    }

    fun toggleConsole() { consoleVisible = !consoleVisible }
    fun toggleProjectPanel() { projectPanelVisible = !projectPanelVisible }
    fun hideProjectPanel() { projectPanelVisible = false }
    fun selectBottomTab(tab: BottomTab) { bottomTab = tab }

    fun openProject(root: File) {
        project = Project(root.name, root)
    }

    fun openFile(file: File) {
        if (!file.isFile) return
        val existing = openFiles.indexOfFirst { it.file == file }
        if (existing >= 0) {
            activeFileIndex = existing
            refreshDiagnostics()
            return
        }
        val content = runCatching { file.readText() }.getOrDefault("")
        openFiles.add(OpenFile(file, content))
        activeFileIndex = openFiles.lastIndex
        refreshDiagnostics()
    }

    fun selectTab(index: Int) {
        if (index in openFiles.indices) {
            activeFileIndex = index
            refreshDiagnostics()
        }
    }

    fun closeTab(index: Int) {
        if (index !in openFiles.indices) return
        openFiles.removeAt(index)
        activeFileIndex = when {
            openFiles.isEmpty() -> -1
            activeFileIndex >= openFiles.size -> openFiles.lastIndex
            else -> activeFileIndex.coerceAtMost(openFiles.lastIndex)
        }
        refreshDiagnostics()
    }

    fun updateActiveContent(newText: String) {
        val idx = activeFileIndex
        val current = openFiles.getOrNull(idx) ?: return
        if (current.content == newText) return
        openFiles[idx] = current.copy(content = newText, dirty = true)
        refreshDiagnostics()
    }

    private fun refreshDiagnostics() {
        diagnostics.clear()
        val file = activeFile ?: return
        diagnostics.addAll(DiagnosticsEngine.analyze(file.content, Language.fromFileName(file.name)))
    }

    fun saveActive() {
        val idx = activeFileIndex
        val current = openFiles.getOrNull(idx) ?: return
        runCatching { current.file.writeText(current.content) }
        openFiles[idx] = current.copy(dirty = false)
    }

    fun saveAll() {
        openFiles.forEachIndexed { i, f ->
            if (f.dirty) {
                runCatching { f.file.writeText(f.content) }
                openFiles[i] = f.copy(dirty = false)
            }
        }
    }

    fun runBuild(scope: CoroutineScope, action: BuildAction) {
        val proj = project ?: return
        if (building) return
        saveAll()
        consoleVisible = true
        building = true
        val language = activeFile?.let { Language.fromFileName(it.name) }
            ?: Language.fromFileName(proj.root.listFiles()?.firstOrNull()?.name ?: "")
        consoleOutput.add(BuildLine("${tr(StringKey.BUILD_STARTED)}: ${action.name} (${language.displayName})", false))
        scope.launch {
            val code = buildRunner.run(proj, language, action) { line ->
                consoleOutput.add(line)
            }
            consoleOutput.add(
                if (code == 0) BuildLine(tr(StringKey.BUILD_FINISHED), false)
                else BuildLine("${tr(StringKey.BUILD_FAILED)} (exit $code)", true)
            )
            building = false
        }
    }

    fun stopBuild() {
        buildRunner.stop()
        building = false
    }

    fun clearConsole() = consoleOutput.clear()

    fun clearTerminal() = terminalOutput.clear()

    /**
     * Runs a shell command in the in-app terminal and streams its output.
     * `cd` and `clear` are handled internally; everything else goes to the
     * system shell (`/system/bin/sh` on Android, `/bin/sh` elsewhere).
     */
    fun runTerminal(scope: CoroutineScope, command: String) {
        val cmd = command.trim()
        if (cmd.isEmpty()) return
        val cwd = terminalCwd ?: (project?.root ?: projectsDir).also { terminalCwd = it }
        terminalOutput.add(BuildLine("${cwd.name} $ $cmd", false))

        when {
            cmd == "clear" -> { terminalOutput.clear(); return }
            cmd == "cd" || cmd.startsWith("cd ") -> {
                val arg = cmd.removePrefix("cd").trim()
                val target = when {
                    arg.isEmpty() -> project?.root ?: projectsDir
                    arg.startsWith("/") -> File(arg)
                    else -> File(cwd, arg)
                }
                if (target.isDirectory) terminalCwd = target.canonicalFile
                else terminalOutput.add(BuildLine("cd: no such directory: $arg", true))
                return
            }
        }

        val shell = listOf("/system/bin/sh", "/bin/sh").firstOrNull { File(it).exists() } ?: "sh"
        scope.launch(Dispatchers.IO) {
            try {
                val process = ProcessBuilder(shell, "-c", cmd)
                    .directory(cwd)
                    .redirectErrorStream(true)
                    .start()
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { terminalOutput.add(BuildLine(it, false)) }
                }
                val code = process.waitFor()
                if (code != 0) terminalOutput.add(BuildLine("(exit $code)", true))
            } catch (e: Exception) {
                terminalOutput.add(BuildLine("error: ${e.message}", true))
            }
        }
    }
}
