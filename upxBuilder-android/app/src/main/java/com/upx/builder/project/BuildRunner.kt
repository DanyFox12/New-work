package com.upx.builder.project

import com.upx.builder.editor.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** What the user asked the toolchain to do. */
enum class BuildAction { BUILD, RUN, CLEAN }

/** One line of build output, tagged so the console can colour errors differently. */
data class BuildLine(val text: String, val isError: Boolean)

/**
 * Invokes the real toolchain for a project (flutter / cmake+make / javac+java /
 * gradle) and streams the output back line by line. If the tool is not installed
 * the runner reports that cleanly instead of crashing — upxBuilder edits code on
 * any machine, and builds where the SDKs are present.
 */
class BuildRunner(
    /** Extra environment (PATH, PREFIX, …) so tools installed into the app's
     *  own toolchain prefix are found — empty on platforms without one. */
    private val extraEnv: Map<String, String> = emptyMap(),
) {

    @Volatile
    private var current: Process? = null

    private fun searchPath(): List<String> =
        (extraEnv["PATH"] ?: System.getenv("PATH") ?: "").split(":").filter { it.isNotBlank() }

    fun stop() {
        current?.destroy()
        current = null
    }

    suspend fun run(
        project: Project,
        language: Language,
        action: BuildAction,
        onLine: (BuildLine) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val command = commandFor(language, action, project)
        if (command == null) {
            onLine(BuildLine("No ${action.name.lowercase()} command defined for ${language.displayName}.", true))
            return@withContext -1
        }
        val tool = command.first()
        if (!isToolAvailable(tool)) {
            onLine(BuildLine("'$tool' was not found on PATH. Install it to ${action.name.lowercase()} ${language.displayName} projects.", true))
            return@withContext -1
        }

        onLine(BuildLine("\$ ${command.joinToString(" ")}", false))
        return@withContext try {
            val builder = ProcessBuilder(command)
                .directory(project.root)
                .redirectErrorStream(true)
            builder.environment().putAll(extraEnv)
            val process = builder.start()
            current = process
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val isError = line.contains("error", ignoreCase = true) ||
                        line.contains("fatal", ignoreCase = true)
                    onLine(BuildLine(line, isError))
                }
            }
            val code = process.waitFor()
            current = null
            code
        } catch (e: Exception) {
            onLine(BuildLine("Failed to start build: ${e.message}", true))
            -1
        }
    }

    /** The command line that builds/runs/cleans a [project] in [language], or
     *  null if the action is undefined. Public so callers can run it inside the
     *  on-device Linux environment (proot/Alpine) instead of the host shell. */
    fun commandFor(language: Language, action: BuildAction, project: Project): List<String>? =
        when (language) {
            Language.DART -> when (action) {
                BuildAction.BUILD -> listOf("flutter", "build", "apk", "--debug")
                BuildAction.RUN -> listOf("flutter", "run")
                BuildAction.CLEAN -> listOf("flutter", "clean")
            }
            Language.CPP -> when (action) {
                BuildAction.BUILD -> listOf("cmake", "--build", "build")
                BuildAction.RUN -> listOf("cmake", "--build", "build", "--target", project.name)
                BuildAction.CLEAN -> listOf("cmake", "-E", "remove_directory", "build")
            }
            Language.JAVA -> when (action) {
                BuildAction.BUILD, BuildAction.RUN ->
                    listOf("javac", "-d", "out", "src/Main.java")
                BuildAction.CLEAN -> listOf("rm", "-rf", "out")
            }
            Language.KOTLIN -> when (action) {
                BuildAction.BUILD -> listOf("gradle", "build")
                BuildAction.RUN -> listOf("gradle", "run")
                BuildAction.CLEAN -> listOf("gradle", "clean")
            }
            Language.PYTHON -> when (action) {
                BuildAction.BUILD -> listOf("python3", "-m", "py_compile", "main.py")
                BuildAction.RUN -> listOf("python3", "main.py")
                BuildAction.CLEAN -> listOf("rm", "-rf", "__pycache__")
            }
            Language.JAVASCRIPT -> when (action) {
                BuildAction.BUILD -> listOf("node", "--check", "index.js")
                BuildAction.RUN -> listOf("node", "index.js")
                BuildAction.CLEAN -> listOf("rm", "-rf", "node_modules")
            }
            Language.GO -> when (action) {
                BuildAction.BUILD -> listOf("go", "build", "./...")
                BuildAction.RUN -> listOf("go", "run", ".")
                BuildAction.CLEAN -> listOf("go", "clean")
            }
            Language.PLAIN -> null
        }

    private fun isToolAvailable(tool: String): Boolean {
        if (searchPath().any { dir -> File(dir, tool).canExecute() }) return true
        return try {
            val which = if (System.getProperty("os.name").startsWith("Windows")) "where" else "which"
            ProcessBuilder(which, tool).start().waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }
}
