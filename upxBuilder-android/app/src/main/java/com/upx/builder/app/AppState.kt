package com.upx.builder.app

import android.content.Context
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
import com.upx.builder.project.ToolchainManager
import com.upx.builder.theme.AppTheme
import com.upx.builder.theme.Themes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/** Which tab the bottom panel is showing. */
enum class BottomTab { CONSOLE, PROBLEMS, TERMINAL }

/**
 * Single source of truth for the running application: the active theme, UI
 * language, open project, editor tabs and console output. UI components read
 * from here and call its methods; Compose recomposes on change.
 *
 * On Android, theme and language choices persist via [android.content.SharedPreferences],
 * and projects live under the app's external files directory so the file tree and
 * editor work within scoped storage without extra permissions.
 */
class AppState(context: Context) {
    private val prefs = context.getSharedPreferences("upxbuilder", Context.MODE_PRIVATE)

    /** Where new projects are created — app-scoped, no runtime permission needed. */
    val projectsDir: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "projects")
        .apply { mkdirs() }

    /** On-device toolchain prefix (Termux-style): real tools live here. */
    val toolchain = ToolchainManager(context.filesDir)

    var theme by mutableStateOf(Themes.byId(prefs.getString("theme", Themes.default.id) ?: Themes.default.id))
        private set

    var language by mutableStateOf(AppLanguage.fromCode(prefs.getString("language", "en") ?: "en"))
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

    /** When true the bottom panel (terminal/console) covers the whole screen, Termux-style. */
    var bottomPanelExpanded by mutableStateOf(false)
        private set

    var projectPanelVisible by mutableStateOf(true)
        private set

    var building by mutableStateOf(false)
        private set

    private val buildRunner = BuildRunner(toolchain.environment())

    val activeFile: OpenFile? get() = openFiles.getOrNull(activeFileIndex)

    fun tr(key: StringKey): String = Strings.get(language, key)

    fun applyTheme(newTheme: AppTheme) {
        theme = newTheme
        prefs.edit().putString("theme", newTheme.id).apply()
    }

    fun applyLanguage(newLanguage: AppLanguage) {
        language = newLanguage
        prefs.edit().putString("language", newLanguage.code).apply()
    }

    fun toggleConsole() { consoleVisible = !consoleVisible }

    fun toggleBottomPanelExpanded() { bottomPanelExpanded = !bottomPanelExpanded }
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
     * system shell (`/system/bin/sh` on Android).
     */
    fun runTerminal(scope: CoroutineScope, command: String) {
        val cmd = command.trim()
        if (cmd.isEmpty()) return
        val cwd = terminalCwd ?: (project?.root ?: projectsDir).also { terminalCwd = it }
        terminalOutput.add(BuildLine("${cwd.name} $ $cmd", false))

        when {
            cmd == "clear" -> { terminalOutput.clear(); return }
            cmd == "pkg" || cmd.startsWith("pkg ") -> { handlePkg(scope, cmd); return }
            cmd == "alpine" || cmd.startsWith("alpine ") -> {
                val inner = cmd.removePrefix("alpine").trim()
                if (inner.isEmpty()) {
                    terminalOutput.add(BuildLine("usage: alpine <command>   (runs the command inside Alpine Linux)", false))
                } else {
                    runInAlpineFromTerminal(scope, inner, cwd)
                }
                return
            }
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

        // Tools that are not on the host (cmake, clang, python3, node, …) run
        // inside the Alpine environment automatically once it is installed.
        val firstWord = cmd.split(Regex("\\s+")).first()
        val onHost = toolchain.environment()["PATH"].orEmpty().split(":")
            .any { dir -> dir.isNotBlank() && File(dir, firstWord).canExecute() }
        if (!onHost && toolchain.alpineInstalled) {
            runInAlpineFromTerminal(scope, cmd, cwd)
            return
        }

        val shell = listOf("/system/bin/sh", "/bin/sh").firstOrNull { File(it).exists() } ?: "sh"
        scope.launch(Dispatchers.IO) {
            try {
                val builder = ProcessBuilder(shell, "-c", cmd)
                    .directory(cwd)
                    .redirectErrorStream(true)
                builder.environment().putAll(toolchain.environment())
                val process = builder.start()
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

    /** Runs [cmd] inside the Alpine environment with the projects dir visible. */
    private fun runInAlpineFromTerminal(scope: CoroutineScope, cmd: String, cwd: File) {
        if (!toolchain.alpineInstalled) {
            terminalOutput.add(BuildLine("Alpine is not installed yet. Run: pkg install alpine", true))
            return
        }
        scope.launch(Dispatchers.IO) {
            val binds = listOf(projectsDir)
            val workDir = if (cwd.absolutePath.startsWith(projectsDir.absolutePath)) cwd else null
            val code = toolchain.runInAlpine(cmd, workDir, binds) { line, isError ->
                terminalOutput.add(BuildLine(line, isError))
            }
            if (code != 0) terminalOutput.add(BuildLine("(exit $code)", true))
        }
    }

    /** Friendly names -> Alpine package names. */
    private val pkgAliases = mapOf(
        "python" to "python3",
        "node" to "nodejs",
        "nodejs" to "nodejs npm",
        "gcc" to "build-base",
        "g++" to "build-base",
        "java" to "openjdk17",
        "javac" to "openjdk17",
    )

    /**
     * Built-in package manager.
     *  - `pkg install busybox` installs BusyBox into $PREFIX/bin (300+ commands).
     *  - `pkg install alpine` sets up the Alpine Linux environment (proot).
     *  - `pkg install <anything else>` installs real Alpine packages — cmake,
     *    clang, make, python3, nodejs, openjdk17, go, … — setting up Alpine
     *    first automatically when needed.
     */
    private fun handlePkg(scope: CoroutineScope, cmd: String) {
        val parts = cmd.split(Regex("\\s+"))
        val action = parts.getOrNull(1)
        val target = parts.getOrNull(2)
        when {
            action == "install" && target == "busybox" -> {
                if (toolchain.busyboxInstalled) {
                    terminalOutput.add(BuildLine("busybox is already installed in PREFIX/bin.", false))
                    return
                }
                scope.launch(Dispatchers.IO) {
                    toolchain.installBusybox { line, isError ->
                        terminalOutput.add(BuildLine(line, isError))
                    }
                }
            }
            action == "install" && target == "alpine" -> {
                scope.launch(Dispatchers.IO) {
                    toolchain.installAlpine { line, isError ->
                        terminalOutput.add(BuildLine(line, isError))
                    }
                }
            }
            action == "install" && target == "flutter" -> {
                terminalOutput.add(BuildLine("Flutter does not ship an Android-hosted SDK yet, so it cannot run on-device.", true))
                terminalOutput.add(BuildLine("You can still edit Flutter projects here and build them in the cloud.", false))
            }
            action == "install" && target != null -> {
                val packages = parts.drop(2).joinToString(" ") { pkgAliases[it] ?: it }
                scope.launch(Dispatchers.IO) {
                    if (!toolchain.alpineInstalled) {
                        terminalOutput.add(BuildLine("Setting up the Alpine Linux environment first (one-time, ~4 MB)…", false))
                        val ok = toolchain.installAlpine { line, isError ->
                            terminalOutput.add(BuildLine(line, isError))
                        }
                        if (!ok) return@launch
                    }
                    terminalOutput.add(BuildLine("apk add $packages", false))
                    val code = toolchain.runInAlpine(
                        "apk update >/dev/null 2>&1; apk add $packages",
                        null, emptyList(),
                    ) { line, isError -> terminalOutput.add(BuildLine(line, isError)) }
                    terminalOutput.add(
                        if (code == 0) BuildLine("Installed: $packages — just type the command to use it.", false)
                        else BuildLine("apk failed (exit $code). Try: pkg search <name>", true)
                    )
                }
            }
            (action == "uninstall" || action == "remove") && target != null -> {
                val packages = parts.drop(2).joinToString(" ") { pkgAliases[it] ?: it }
                runInAlpineFromTerminal(scope, "apk del $packages", projectsDir)
            }
            action == "search" && target != null -> {
                runInAlpineFromTerminal(scope, "apk update >/dev/null 2>&1; apk search ${parts.drop(2).joinToString(" ")}", projectsDir)
            }
            action == "list" -> {
                val tools = toolchain.bin.listFiles()?.map { it.name }?.sorted() ?: emptyList()
                if (tools.isEmpty()) terminalOutput.add(BuildLine("No packages installed. Try: pkg install busybox", false))
                else terminalOutput.add(BuildLine(tools.joinToString("  "), false))
                if (toolchain.alpineInstalled) runInAlpineFromTerminal(scope, "apk info", projectsDir)
            }
            else -> {
                terminalOutput.add(BuildLine("usage: pkg install <name…> | pkg uninstall <name…> | pkg search <name> | pkg list", false))
                terminalOutput.add(BuildLine("examples: pkg install busybox | pkg install cmake clang make | pkg install python3", false))
            }
        }
    }
}
