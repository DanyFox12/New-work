package com.upx.builder.project

import android.os.Build
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages upxBuilder's on-device toolchain prefix ($PREFIX) — the directory
 * where real command-line tools live. This is the same approach Termux and
 * AndroidIDE use: tools are downloaded into the app's private storage and
 * executed from there.
 *
 * Two hard Android rules shape this class:
 *  - Binaries MUST live in the app's internal files dir; external/shared
 *    storage is mounted `noexec` and nothing there can ever run.
 *  - Executing app-private binaries requires targetSdkVersion <= 28
 *    (the app's build.gradle pins this, exactly like Termux/AndroidIDE).
 */
class ToolchainManager(filesDir: File) {

    val prefix = File(filesDir, "usr")
    val bin = File(prefix, "bin")
    val lib = File(prefix, "lib")
    val tmp = File(prefix, "tmp")
    val home = File(filesDir, "home")

    init {
        bin.mkdirs(); lib.mkdirs(); tmp.mkdirs(); home.mkdirs()
    }

    /** Environment for terminal commands and builds so $PREFIX tools are found. */
    fun environment(): Map<String, String> = mapOf(
        "PREFIX" to prefix.absolutePath,
        "HOME" to home.absolutePath,
        "TMPDIR" to tmp.absolutePath,
        "PATH" to bin.absolutePath + ":" + (System.getenv("PATH") ?: "/system/bin"),
        "LD_LIBRARY_PATH" to lib.absolutePath,
        "TERM" to "dumb",
    )

    private val busybox = File(bin, "busybox")

    val busyboxInstalled: Boolean get() = busybox.canExecute()

    /**
     * Statically linked BusyBox builds (musl libc, zero dependencies) — static
     * binaries need no shared libraries, so they run on Android kernels as-is.
     */
    private fun busyboxUrl(): String? {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return null
        val variant = when {
            abi.startsWith("arm64") -> "busybox-armv8l"
            abi.startsWith("armeabi") -> "busybox-armv7l"
            abi.startsWith("x86_64") -> "busybox-x86_64"
            abi.startsWith("x86") -> "busybox-i686"
            else -> return null
        }
        return "https://busybox.net/downloads/binaries/1.31.0-defconfig-multiarch-musl/$variant"
    }

    /**
     * Downloads BusyBox for this device's CPU, marks it executable, runs it to
     * list its applets, and symlinks every applet into $PREFIX/bin so 300+ real
     * Unix commands (ls, grep, vi, tar, wget, unzip, …) work by name.
     */
    suspend fun installBusybox(onLine: (String, Boolean) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val url = busyboxUrl()
        if (url == null) {
            onLine("Unsupported CPU: ${Build.SUPPORTED_ABIS.joinToString()}", true)
            return@withContext false
        }
        try {
            onLine("Downloading $url …", false)
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000
            conn.readTimeout = 120_000
            conn.instanceFollowRedirects = true
            conn.inputStream.use { input ->
                busybox.outputStream().use { output -> input.copyTo(output) }
            }
            conn.disconnect()
            onLine("Downloaded ${busybox.length() / 1024} KB.", false)

            if (!busybox.setExecutable(true, false)) {
                onLine("Could not mark busybox as executable.", true)
                return@withContext false
            }

            // Running the binary we just downloaded both verifies that on-device
            // execution works and gives us the applet list to link.
            val applets = try {
                val p = ProcessBuilder(busybox.absolutePath, "--list")
                    .redirectErrorStream(true).start()
                val list = p.inputStream.bufferedReader().readLines()
                    .map { it.trim() }.filter { it.isNotEmpty() && !it.contains(" ") }
                p.waitFor()
                list
            } catch (e: Exception) {
                onLine("busybox downloaded but failed to execute: ${e.message}", true)
                onLine("(execution requires the app to target SDK 28 — rebuild needed?)", true)
                return@withContext false
            }
            if (applets.isEmpty()) {
                onLine("busybox ran but reported no applets.", true)
                return@withContext false
            }

            var linked = 0
            applets.forEach { name ->
                val link = File(bin, name)
                if (name != "busybox" && !link.exists()) {
                    runCatching { Os.symlink(busybox.absolutePath, link.absolutePath); linked++ }
                }
            }
            onLine("Installed $linked commands into \$PREFIX/bin.", false)
            onLine("Toolchain ready — try: ls, uname -a, vi, wget, tar.", false)
            true
        } catch (e: Exception) {
            onLine("Install failed: ${e.message}", true)
            false
        }
    }
}
