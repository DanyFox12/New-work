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
import com.upx.builder.project.InstallMethod
import com.upx.builder.project.OpenFile
import com.upx.builder.project.Project
import com.upx.builder.project.ToolchainManager
import com.upx.builder.project.Toolchains
import com.upx.builder.theme.AppTheme
import com.upx.builder.theme.Themes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    /** Where projects live. Internal storage on purpose: Android mounts external
     *  storage `noexec`, so programs the user compiles could never run there.
     *  Keeping projects internal lets `./a.out` work, exactly like Termux's home. */
    val projectsDir: File = File(context.filesDir, "projects").apply {
        val legacy = File(context.getExternalFilesDir(null) ?: context.filesDir, "projects")
        val firstRun = !exists()
        mkdirs()
        if (firstRun && legacy != this && legacy.isDirectory) {
            runCatching { legacy.copyRecursively(this, overwrite = false) }
        }
    }

    /** On-device toolchain prefix (Termux-style): real tools live here. */
    val toolchain = ToolchainManager(context.filesDir, context.assets)

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

    /** Working directory of the Alpine shell (may be an Alpine-only path like /root). */
    var alpineCwd by mutableStateOf(projectsDir.absolutePath)
        private set

    /** Prompt label shown by the terminal UI. */
    val terminalPrompt: String
        get() = if (toolchain.alpineInstalled) alpineCwd.substringAfterLast('/').ifEmpty { "/" }
        else terminalCwd?.name ?: "~"

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

    /** First-launch offer to download the dev environment automatically.
     *  Shown once; never again after the user answers (or once tools exist). */
    var setupPromptVisible by mutableStateOf(
        !prefs.getBoolean("setup_prompted", false) && !toolchain.alpineInstalled
    )
        private set

    fun dismissSetupPrompt() {
        setupPromptVisible = false
        prefs.edit().putBoolean("setup_prompted", true).apply()
    }

    /** "Install now" on the first-launch prompt: essentials = the Linux
     *  environment + BusyBox; everything = essentials + the core dev set. */
    fun acceptSetupPrompt(everything: Boolean) {
        dismissSetupPrompt()
        installTool(if (everything) "busybox all" else "busybox alpine")
    }

    private val buildRunner = BuildRunner(toolchain.environment())

    /** App-level scope for terminal commands, installs and builds. Deliberately
     *  NOT a UI composition scope: those cancel on recomposition (keyboard,
     *  tab switch), which silently killed long installs mid-way. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        if (toolchain.alpineInstalled) {
            terminalOutput.add(BuildLine("Linux environment ready. Add tools with: pkg install python java cmake flutter sdk …", false))
            terminalOutput.add(BuildLine("Tip: open Setup (toolbar) for one-tap installs, or run 'pkg help'.", false))
        } else {
            terminalOutput.add(BuildLine("Welcome! Tap Setup in the toolbar, or run 'pkg install alpine' to unlock the full Linux environment (like Termux).", false))
            terminalOutput.add(BuildLine("Then: pkg install all  •  pkg install flutter  •  pkg install sdk  •  pkg help", false))
        }
    }

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
        appScope.launch {
            // Once the Linux environment exists, the real compilers (flutter,
            // cmake, javac, gradle…) live inside it — so run the build there.
            // Before that, fall back to the host shell + $PREFIX tools.
            val code = if (toolchain.alpineInstalled) {
                runBuildInAlpine(proj, language, action) { line -> consoleOutput.add(line) }
            } else {
                buildRunner.run(proj, language, action) { line -> consoleOutput.add(line) }
            }
            consoleOutput.add(
                if (code == 0) BuildLine(tr(StringKey.BUILD_FINISHED), false)
                else BuildLine("${tr(StringKey.BUILD_FAILED)} (exit $code)", true)
            )
            building = false
        }
    }

    /**
     * Runs the project's build/run/clean command inside the Alpine environment.
     * The projects dir is bound at its real path, so the project root resolves to
     * the same absolute path inside Alpine and the build's outputs land back in
     * the user's project. Installed tools (flutter, cmake, gradle, the Android
     * SDK…) are on the PATH because [ToolchainManager] sources /etc/profile.d.
     */
    private suspend fun runBuildInAlpine(
        proj: Project,
        language: Language,
        action: BuildAction,
        onLine: (BuildLine) -> Unit,
    ): Int {
        val command = buildRunner.commandFor(language, action, proj)
        if (command == null) {
            onLine(BuildLine("No ${action.name.lowercase()} command defined for ${language.displayName}.", true))
            return -1
        }
        val shellCmd = command.joinToString(" ") { shellQuote(it) }
        onLine(BuildLine("\$ $shellCmd", false))
        val tool = command.first()
        val check = "command -v ${shellQuote(tool)} >/dev/null 2>&1 || " +
            "{ echo \"'$tool' is not installed. Open Setup or run: pkg install $tool\"; exit 127; }\n"
        // Bind the project itself (so it is visible at its real path even if it
        // lives outside the default projects home) plus the toolchain home.
        val binds = listOf(projectsDir, toolchain.home, proj.root).distinct()
        return toolchain.runInAlpine(check + shellCmd, proj.root, binds) { line, isError ->
            val err = isError || line.contains("error", ignoreCase = true) || line.contains("fatal", ignoreCase = true)
            onLine(BuildLine(line, err))
        }
    }

    /** Single-quotes a shell argument so paths/names with spaces stay intact. */
    private fun shellQuote(arg: String): String = "'" + arg.replace("'", "'\\''") + "'"

    fun stopBuild() {
        buildRunner.stop()
        building = false
    }

    fun clearConsole() = consoleOutput.clear()

    fun clearTerminal() = terminalOutput.clear()

    /**
     * Runs a shell command in the in-app terminal and streams its output.
     *
     * Once Alpine Linux is installed, the terminal IS an Alpine shell — every
     * command runs inside the full Linux environment (like Termux), with `cd`
     * persisting between commands, including Alpine-only paths like /root.
     * Before that, commands go to the host shell (`/system/bin/sh` + busybox).
     */
    fun runTerminal(scope: CoroutineScope, command: String) {
        val cmd = command.trim()
        if (cmd.isEmpty()) return
        terminalOutput.add(BuildLine("$terminalPrompt $ $cmd", false))

        when {
            cmd == "clear" -> { terminalOutput.clear(); return }
            cmd == "pkg" || cmd.startsWith("pkg ") -> { handlePkg(scope, cmd); return }
        }

        if (toolchain.alpineInstalled) {
            runAlpineShell(scope, cmd)
            return
        }

        // ---- Host fallback (before Alpine is installed) ----
        val cwd = terminalCwd ?: (project?.root ?: projectsDir).also { terminalCwd = it }
        if (cmd == "cd" || cmd.startsWith("cd ")) {
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

        val shell = listOf("/system/bin/sh", "/bin/sh").firstOrNull { File(it).exists() } ?: "sh"
        appScope.launch(Dispatchers.IO) {
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
                if (code != 0) terminalOutput.add(BuildLine(exitLabel(code), true))
            } catch (e: Exception) {
                terminalOutput.add(BuildLine("error: ${e.message}", true))
            }
        }
    }

    /**
     * Runs [cmd] in the Alpine shell. The working directory persists between
     * commands: the wrapper script re-enters [alpineCwd], runs the command,
     * then reports the resulting directory back on a marker line.
     */
    private fun runAlpineShell(scope: CoroutineScope, cmd: String) {
        appScope.launch(Dispatchers.IO) {
            val marker = "__UPXCWD__"
            val script = buildString {
                append("cd \"").append(alpineCwd).append("\" 2>/dev/null || cd /root\n")
                append(cmd).append("\n")
                append("__upx_rc=\$?\n")
                append("printf '").append(marker).append("%s\\n' \"\$PWD\"\n")
                append("exit \$__upx_rc\n")
            }
            val binds = listOf(projectsDir, toolchain.home)
            val code = toolchain.runInAlpine(script, null, binds) { line, isError ->
                if (line.startsWith(marker)) alpineCwd = line.removePrefix(marker)
                else terminalOutput.add(BuildLine(line, isError))
            }
            if (code != 0) terminalOutput.add(BuildLine(exitLabel(code), true))
        }
    }

    /** Human-readable exit status; decodes signal deaths (e.g. 159 = SIGSYS). */
    private fun exitLabel(code: Int): String {
        if (code <= 128) return "(exit $code)"
        val sig = code - 128
        return when (sig) {
            31 -> "(killed by SIGSYS - this device's security filter blocked the binary)"
            9 -> "(killed by SIGKILL - out of memory or stopped by the system)"
            11 -> "(crashed with SIGSEGV)"
            else -> "(killed by signal $sig)"
        }
    }

    /** Runs [cmd] inside the Alpine environment with the projects dir visible. */
    private fun runInAlpineFromTerminal(scope: CoroutineScope, cmd: String, cwd: File) {
        if (!toolchain.alpineInstalled) {
            terminalOutput.add(BuildLine("Alpine is not installed yet. Run: pkg install alpine", true))
            return
        }
        appScope.launch(Dispatchers.IO) {
            val binds = listOf(projectsDir)
            val workDir = if (cwd.absolutePath.startsWith(projectsDir.absolutePath)) cwd else null
            val code = toolchain.runInAlpine(cmd, workDir, binds) { line, isError ->
                terminalOutput.add(BuildLine(line, isError))
            }
            if (code != 0) terminalOutput.add(BuildLine(exitLabel(code), true))
        }
    }

    /**
     * Starts installing a toolchain by id (e.g. "flutter", "sdk", "python") and
     * brings the Terminal into view so the user can watch the progress. Used by
     * the Setup guide's one-tap Install buttons.
     */
    fun installTool(id: String) {
        consoleVisible = true
        selectBottomTab(BottomTab.TERMINAL)
        runTerminal(appScope, "pkg install $id")
    }

    /** Ensures the Alpine Linux environment exists, installing it if needed. */
    private suspend fun ensureAlpine(): Boolean {
        if (toolchain.alpineInstalled) return true
        terminalOutput.add(BuildLine("Setting up the Alpine Linux environment first (one-time, ~4 MB)…", false))
        return toolchain.installAlpine { line, isError -> terminalOutput.add(BuildLine(line, isError)) }
    }

    /** Installs a single toolchain token, dispatching on how it is delivered. */
    private suspend fun installOne(token: String): Boolean {
        val emit: (String, Boolean) -> Unit = { line, isError -> terminalOutput.add(BuildLine(line, isError)) }
        val tc = Toolchains.byId(token)
        return when (val method = tc?.method) {
            InstallMethod.Busybox -> {
                if (toolchain.busyboxInstalled) { emit("busybox is already installed.", false); true }
                else toolchain.installBusybox(emit)
            }
            InstallMethod.Alpine -> ensureAlpine()
            InstallMethod.AndroidSdk -> ensureAlpine() && toolchain.installAndroidSdk(emit)
            InstallMethod.Flutter -> ensureAlpine() && toolchain.installFlutter(emit)
            InstallMethod.Gradle -> ensureAlpine() && toolchain.installGradle(emit)
            is InstallMethod.Apk -> ensureAlpine() && toolchain.apkAdd(method.packages, emit)
            // Not in the curated catalogue: treat it as a raw Alpine package name.
            null -> ensureAlpine() && toolchain.apkAdd(token, emit)
        }
    }

    private fun pkgUsage() {
        terminalOutput.add(BuildLine("usage: pkg <install|uninstall|search|list|help> <name…>", false))
        terminalOutput.add(BuildLine("  pkg install alpine        one-time: the full Linux environment", false))
        terminalOutput.add(BuildLine("  pkg install python java cmake clang    dev toolchains", false))
        terminalOutput.add(BuildLine("  pkg install sdk           Android command-line tools (sdkmanager)", false))
        terminalOutput.add(BuildLine("  pkg install platform-tools   adb & fastboot", false))
        terminalOutput.add(BuildLine("  pkg install flutter       Flutter + Dart SDK", false))
        terminalOutput.add(BuildLine("  pkg install all           the common native dev set", false))
        terminalOutput.add(BuildLine("available: " + Toolchains.all.joinToString(", ") { it.id }, false))
    }

    /**
     * Built-in package manager — `pkg`, modelled on Termux's. It installs real,
     * arch-native tools into the on-device Linux environment: compilers (gcc,
     * clang, cmake), languages (python, java, node, go), the Android SDK manager,
     * Flutter/Dart, Gradle, adb/fastboot and more. The whole catalogue lives in
     * [Toolchains], shared with the in-app Setup guide.
     */
    private fun handlePkg(scope: CoroutineScope, cmd: String) {
        val parts = cmd.split(Regex("\\s+")).filter { it.isNotBlank() }
        val action = parts.getOrNull(1)?.lowercase()
        val targets = parts.drop(2)
        when (action) {
            "install", "add", "i" -> {
                if (targets.isEmpty()) { pkgUsage(); return }
                // Expand "all" in place so e.g. `pkg install busybox all` keeps
                // its order, and de-duplicate.
                val list = targets.flatMap { t ->
                    if (t.lowercase() in setOf("all", "everything")) Toolchains.coreSetup else listOf(t)
                }.distinct()
                if (list.size > targets.size) {
                    terminalOutput.add(BuildLine("Core dev set: ${Toolchains.coreSetup.joinToString(" ")}", false))
                    terminalOutput.add(BuildLine("(The large SDKs are separate: pkg install sdk | flutter)", false))
                }
                appScope.launch(Dispatchers.IO) {
                    val failed = mutableListOf<String>()
                    for (t in list) {
                        terminalOutput.add(BuildLine("── installing $t ──", false))
                        if (installOne(t)) continue
                        failed += t
                        // Everything except BusyBox runs inside the Linux
                        // environment; if THAT is broken, the remaining installs
                        // would all fail the same way — stop instead of spamming.
                        val needsAlpine = Toolchains.byId(t)?.method != InstallMethod.Busybox
                        if (needsAlpine && !toolchain.alpineInstalled) {
                            terminalOutput.add(BuildLine("The Linux environment could not start, so the remaining tools were skipped.", true))
                            terminalOutput.add(BuildLine("The lines above show the exact reason. Once fixed, retry: pkg install ${list.joinToString(" ")}", true))
                            return@launch
                        }
                    }
                    terminalOutput.add(
                        if (failed.isEmpty()) {
                            BuildLine("Done. Type a tool's name to use it (e.g. python3, javac, cmake).", false)
                        } else {
                            BuildLine("Finished, but these failed: ${failed.joinToString(", ")} — see the errors above. Retry: pkg install ${failed.joinToString(" ")}", true)
                        }
                    )
                }
            }
            "uninstall", "remove", "rm", "del" -> {
                if (targets.isEmpty()) { pkgUsage(); return }
                val packages = targets.joinToString(" ") { token ->
                    (Toolchains.byId(token)?.method as? InstallMethod.Apk)?.packages ?: token
                }
                runInAlpineFromTerminal(scope, "apk del $packages", projectsDir)
            }
            "search", "find" -> {
                if (targets.isEmpty()) { pkgUsage(); return }
                runInAlpineFromTerminal(scope, "apk update >/dev/null 2>&1; apk search ${targets.joinToString(" ")}", projectsDir)
            }
            "list" -> {
                val tools = toolchain.bin.listFiles()?.map { it.name }?.sorted() ?: emptyList()
                if (tools.isNotEmpty()) terminalOutput.add(BuildLine("PREFIX/bin: " + tools.joinToString("  "), false))
                if (toolchain.alpineInstalled) runInAlpineFromTerminal(scope, "apk info", projectsDir)
                else terminalOutput.add(BuildLine("Linux environment not installed yet. Run: pkg install alpine", false))
            }
            else -> pkgUsage()
        }
    }
}
