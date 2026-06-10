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
     * Mirrors of statically linked BusyBox builds (musl libc, zero shared-lib
     * dependencies — they run on Android kernels as-is) for this device's CPU.
     * We try each in order so one mirror being down does not break the install.
     */
    private fun busyboxUrls(): List<String> {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return emptyList()
        val variant = when {
            abi.startsWith("arm64") -> "busybox-armv8l"
            abi.startsWith("armeabi") -> "busybox-armv7l"
            abi.startsWith("x86_64") -> "busybox-x86_64"
            abi.startsWith("x86") -> "busybox-i686"
            else -> return emptyList()
        }
        return listOf(
            "https://busybox.net/downloads/binaries/1.31.0-defconfig-multiarch-musl/$variant",
            "https://frippery.org/files/busybox/$variant",
        )
    }

    /** A safe default applet set, used if `busybox --list` cannot be parsed. */
    private val fallbackApplets = listOf(
        "ls", "cat", "cp", "mv", "rm", "mkdir", "rmdir", "touch", "echo", "pwd",
        "grep", "sed", "awk", "find", "head", "tail", "wc", "sort", "uniq", "cut",
        "tar", "gzip", "gunzip", "unzip", "wget", "vi", "less", "more", "chmod",
        "chown", "ln", "df", "du", "ps", "kill", "uname", "whoami", "id", "env",
        "date", "sleep", "which", "basename", "dirname", "tr", "diff", "xargs",
    )

    /**
     * Downloads BusyBox for this device's CPU (trying each mirror), validates it
     * is a real ELF binary, marks it executable, runs it to confirm on-device
     * execution works, then symlinks every applet into $PREFIX/bin so 300+ real
     * Unix commands (ls, grep, vi, tar, wget, unzip, …) work by name.
     */
    suspend fun installBusybox(onLine: (String, Boolean) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val urls = busyboxUrls()
        if (urls.isEmpty()) {
            onLine("Unsupported CPU: ${Build.SUPPORTED_ABIS.joinToString()}", true)
            return@withContext false
        }

        var downloaded = false
        for (url in urls) {
            if (downloadTo(url, busybox, onLine)) { downloaded = true; break }
            onLine("Trying next mirror…", false)
        }
        if (!downloaded) {
            onLine("All mirrors failed. Check the device's internet connection and retry.", true)
            return@withContext false
        }

        // Reject anything that is not a real ELF executable (e.g. an HTML error
        // page) before we ever try to run it.
        if (!isElf(busybox)) {
            onLine("Downloaded file is not a valid executable (got ${busybox.length()} bytes). Aborting.", true)
            busybox.delete()
            return@withContext false
        }

        if (!busybox.setExecutable(true, false)) {
            onLine("Could not mark busybox as executable.", true)
            return@withContext false
        }

        // Running the binary we just downloaded confirms on-device execution
        // works and gives us the applet list to link.
        val applets = try {
            val p = ProcessBuilder(busybox.absolutePath, "--list")
                .redirectErrorStream(true).start()
            val list = p.inputStream.bufferedReader().readLines()
                .map { it.trim() }.filter { it.isNotEmpty() && !it.contains(" ") }
            p.waitFor()
            list
        } catch (e: Exception) {
            onLine("busybox downloaded but failed to execute: ${e.message}", true)
            onLine("This usually means the app is not targeting SDK 28 — make sure you built v1.4.0+.", true)
            return@withContext false
        }
        val toLink = if (applets.isNotEmpty()) applets else fallbackApplets

        var linked = 0
        toLink.forEach { name ->
            val link = File(bin, name)
            if (name != "busybox") {
                runCatching {
                    if (link.exists()) link.delete()
                    Os.symlink(busybox.absolutePath, link.absolutePath)
                    linked++
                }
            }
        }
        onLine("Installed $linked commands into \$PREFIX/bin.", false)
        onLine("Toolchain ready — try: ls, uname -a, wget, tar, grep.", false)
        true
    }

    /** Downloads [url] to [dest], following redirects and checking the status. */
    private fun downloadTo(url: String, dest: File, onLine: (String, Boolean) -> Unit): Boolean {
        onLine("Downloading $url …", false)
        return try {
            var current = url
            var conn = open(current)
            // Follow up to 5 redirects manually (covers https↔http hops too).
            var redirects = 0
            while (conn.responseCode in listOf(301, 302, 303, 307, 308) && redirects < 5) {
                current = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                conn = open(current)
                redirects++
            }
            if (conn.responseCode != 200) {
                onLine("Mirror returned HTTP ${conn.responseCode}.", true)
                conn.disconnect()
                return false
            }
            conn.inputStream.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            conn.disconnect()
            if (dest.length() < 1024) {
                onLine("Download too small (${dest.length()} bytes) — likely an error page.", true)
                return false
            }
            onLine("Downloaded ${dest.length() / 1024} KB.", false)
            true
        } catch (e: Exception) {
            onLine("Download error: ${e.message}", true)
            false
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "upxBuilder")
        }

    /** True if the first four bytes are the ELF magic number (0x7F 'E' 'L' 'F'). */
    private fun isElf(file: File): Boolean = try {
        file.inputStream().use { input ->
            val head = ByteArray(4)
            input.read(head) == 4 &&
                head[0] == 0x7F.toByte() && head[1] == 'E'.code.toByte() &&
                head[2] == 'L'.code.toByte() && head[3] == 'F'.code.toByte()
        }
    } catch (_: Exception) {
        false
    }
}
