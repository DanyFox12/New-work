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

    /** Root of the Alpine Linux environment (run under proot) that provides
     *  real compilers: cmake, clang, gcc, python3, nodejs, … via `apk`. */
    val alpineRoot = File(filesDir, "alpine")

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

    // ------------------------------------------------------------------
    // Alpine Linux under proot — real compilers on a stock phone.
    //
    // proot is a statically linked, no-root chroot-alternative (it rewrites
    // paths via ptrace). With a ~4 MB Alpine mini root filesystem it gives
    // the app a complete Linux userland whose package manager (`apk`) can
    // install cmake, clang, gcc, make, python3, nodejs, openjdk and
    // thousands of other prebuilt packages. UserLAnd and similar apps use
    // exactly this technique.
    // ------------------------------------------------------------------

    private val proot = File(bin, "proot")

    /** Marker written when this device's proot supports --link2symlink
     *  (needed because app storage forbids hard links, which apk creates). */
    private val link2symlinkMarker = File(prefix, "etc/proot-link2symlink")

    val alpineInstalled: Boolean
        get() = proot.canExecute() && File(alpineRoot, "etc/alpine-release").exists()

    private fun prootUrl(): String? {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return null
        val arch = when {
            abi.startsWith("arm64") -> "aarch64"
            abi.startsWith("armeabi") -> "arm"
            abi.startsWith("x86_64") -> "x86_64"
            else -> return null
        }
        return "https://github.com/proot-me/proot/releases/download/v5.3.0/proot-v5.3.0-$arch-static"
    }

    private fun alpineUrls(): List<String> {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return emptyList()
        val arch = when {
            abi.startsWith("arm64") -> "aarch64"
            abi.startsWith("armeabi") -> "armv7"
            abi.startsWith("x86_64") -> "x86_64"
            abi.startsWith("x86") -> "x86"
            else -> return emptyList()
        }
        val release = "3.20.3"
        val path = "alpine/v3.20/releases/$arch/alpine-minirootfs-$release-$arch.tar.gz"
        return listOf(
            "https://dl-cdn.alpinelinux.org/$path",
            "https://uk.alpinelinux.org/$path",
            "https://mirror.leaseweb.com/$path",
        )
    }

    /**
     * Installs the Alpine environment: downloads proot + the Alpine mini root
     * filesystem, unpacks it with the built-in pure-Kotlin extractor (no
     * downloaded binary needed), configures DNS and apk repositories, then
     * test-runs a shell inside it to confirm proot works on this device.
     */
    suspend fun installAlpine(onLine: (String, Boolean) -> Unit): Boolean = withContext(Dispatchers.IO) {
        if (alpineInstalled) {
            onLine("Alpine Linux is already installed.", false)
            return@withContext true
        }

        val purl = prootUrl()
        if (purl == null) {
            onLine("Unsupported CPU: ${Build.SUPPORTED_ABIS.joinToString()}", true)
            return@withContext false
        }
        if (!proot.canExecute()) {
            if (!downloadTo(purl, proot, onLine)) return@withContext false
            if (!isElf(proot)) {
                onLine("proot download is not a valid executable. Aborting.", true)
                proot.delete()
                return@withContext false
            }
            proot.setExecutable(true, false)
        }

        val tarball = File(tmp, "alpine-minirootfs.tar.gz")
        var downloaded = false
        for (url in alpineUrls()) {
            if (downloadTo(url, tarball, onLine)) { downloaded = true; break }
            onLine("Trying next mirror…", false)
        }
        if (!downloaded) {
            onLine("Could not download Alpine from any mirror. Check the connection and retry.", true)
            return@withContext false
        }

        onLine("Unpacking Alpine Linux…", false)
        alpineRoot.mkdirs()
        val unpacked = extractTarGz(tarball, alpineRoot, onLine)
        tarball.delete()
        if (!unpacked || !File(alpineRoot, "etc/alpine-release").exists()) {
            onLine("Alpine unpack failed.", true)
            return@withContext false
        }

        // DNS + package repositories (main + community = thousands of packages).
        File(alpineRoot, "etc/resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        File(alpineRoot, "etc/apk/repositories").writeText(
            "https://dl-cdn.alpinelinux.org/alpine/v3.20/main\n" +
                "https://dl-cdn.alpinelinux.org/alpine/v3.20/community\n"
        )

        // Confirm proot actually runs on this device, preferring --link2symlink
        // (apk needs it because app storage forbids hard links).
        File(prefix, "etc").mkdirs()
        link2symlinkMarker.delete()
        val withFlag = probeAlpine(link2symlink = true)
        if (withFlag) {
            link2symlinkMarker.writeText("1")
        } else if (!probeAlpine(link2symlink = false)) {
            onLine("proot could not start a shell on this device.", true)
            return@withContext false
        }

        onLine("Alpine Linux ${File(alpineRoot, "etc/alpine-release").readText().trim()} installed!", false)
        onLine("Install real compilers now, e.g.:", false)
        onLine("  pkg install cmake clang make python3 nodejs", false)
        true
    }

    /** Runs `echo` inside Alpine to verify proot works with the given flag. */
    private fun probeAlpine(link2symlink: Boolean): Boolean = try {
        val p = ProcessBuilder(prootArgv(link2symlink, null, emptyList(), "echo upx-ok"))
            .redirectErrorStream(true)
            .apply { environment().putAll(prootEnv()) }
            .start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor() == 0 && out.contains("upx-ok")
    } catch (_: Exception) {
        false
    }

    /**
     * Runs a shell command inside Alpine, streaming merged output to [onLine].
     * [binds] are host directories made visible inside Alpine at the same
     * absolute path, so project files keep their paths; [workDir] must be
     * under one of them (or null for /root).
     */
    fun runInAlpine(
        command: String,
        workDir: File?,
        binds: List<File>,
        onLine: (String, Boolean) -> Unit,
    ): Int = try {
        val p = ProcessBuilder(prootArgv(link2symlinkMarker.exists(), workDir, binds, command))
            .redirectErrorStream(true)
            .apply { environment().putAll(prootEnv()) }
            .start()
        p.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { onLine(it, false) }
        }
        p.waitFor()
    } catch (e: Exception) {
        onLine("alpine error: ${e.message}", true)
        -1
    }

    private fun prootArgv(
        link2symlink: Boolean,
        workDir: File?,
        binds: List<File>,
        command: String,
    ): List<String> {
        val argv = mutableListOf(proot.absolutePath, "-r", alpineRoot.absolutePath)
        if (link2symlink) argv += "--link2symlink"
        argv += listOf("-0", "-b", "/dev", "-b", "/proc", "-b", "/sys")
        binds.forEach { argv += listOf("-b", it.absolutePath) }
        argv += listOf("-w", workDir?.absolutePath ?: "/root")
        // Source any tool environment we installed (Android SDK, Flutter, Gradle
        // drop a script in /etc/profile.d) so freshly installed tools stay on the
        // PATH in every later command and build — without it the user would have
        // to re-export PATH each time.
        val sourced =
            "for f in /etc/profile.d/*.sh; do [ -r \"\$f\" ] && . \"\$f\"; done 2>/dev/null; $command"
        argv += listOf(
            "/usr/bin/env", "-i",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "HOME=/root", "TERM=dumb", "LANG=C.UTF-8",
            "/bin/sh", "-c", sourced,
        )
        return argv
    }

    private fun prootEnv(): Map<String, String> = mapOf(
        // proot keeps its runtime state here; the default /tmp does not exist.
        "PROOT_TMP_DIR" to tmp.absolutePath,
        // Avoids seccomp issues on several Android versions (Termux does the same).
        "PROOT_NO_SECCOMP" to "1",
    )

    // ------------------------------------------------------------------
    // Named toolchain installers. apk packages are arch-native and handled
    // generically by the caller; these cover the headline tools the user asked
    // for that are NOT a single apk package: the Android SDK command-line tools,
    // the Flutter SDK and Gradle. Each persists its PATH via /etc/profile.d so it
    // stays available in every later terminal command and build.
    // ------------------------------------------------------------------

    /** True once the Android command-line tools (sdkmanager) are present. */
    val androidSdkInstalled: Boolean
        get() = File(alpineRoot, "root/android-sdk/cmdline-tools/latest/bin/sdkmanager").exists()

    /** True once a Flutter checkout exists. */
    val flutterInstalled: Boolean
        get() = File(alpineRoot, "root/flutter/bin/flutter").exists()

    /** True once a Gradle distribution is unpacked. */
    val gradleInstalled: Boolean
        get() = File(alpineRoot, "opt/gradle/bin/gradle").exists()

    /** Writes a tiny env script that every later Alpine command sources. */
    private fun writeProfileScript(name: String, body: String) {
        val dir = File(alpineRoot, "etc/profile.d").apply { mkdirs() }
        File(dir, name).writeText("#!/bin/sh\n$body\n")
    }

    /** apk add helper that streams progress to [onLine]; returns true on success. */
    fun apkAdd(packages: String, onLine: (String, Boolean) -> Unit): Boolean {
        onLine("apk add $packages", false)
        val code = runInAlpine(
            "apk update >/dev/null 2>&1; apk add --no-cache $packages",
            null, emptyList(), onLine,
        )
        return code == 0
    }

    /**
     * Installs Google's Android command-line tools (sdkmanager). These are Java,
     * so they run on any CPU once OpenJDK is present. We unpack them into the
     * standard `cmdline-tools/latest` layout, accept the SDK licences, then make
     * sdkmanager/adb reachable on the PATH.
     */
    suspend fun installAndroidSdk(onLine: (String, Boolean) -> Unit): Boolean = withContext(Dispatchers.IO) {
        if (!alpineInstalled) { onLine("Install the Linux environment first: pkg install alpine", true); return@withContext false }
        onLine("Installing Java + tools needed by the Android SDK manager…", false)
        if (!apkAdd("openjdk17 unzip curl", onLine)) return@withContext false

        writeProfileScript(
            "upx-android.sh",
            "export ANDROID_HOME=/root/android-sdk\n" +
                "export ANDROID_SDK_ROOT=\$ANDROID_HOME\n" +
                "export PATH=\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH",
        )

        val script = """
            set -e
            export ANDROID_HOME=/root/android-sdk
            mkdir -p "${'$'}ANDROID_HOME/cmdline-tools"
            cd /tmp
            URL=https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
            echo "Downloading Android command-line tools (~120 MB)…"
            curl -L --fail -o cmdtools.zip "${'$'}URL"
            rm -rf "${'$'}ANDROID_HOME/cmdline-tools/latest"
            unzip -q cmdtools.zip -d "${'$'}ANDROID_HOME/cmdline-tools"
            mv "${'$'}ANDROID_HOME/cmdline-tools/cmdline-tools" "${'$'}ANDROID_HOME/cmdline-tools/latest"
            rm -f cmdtools.zip
            echo "Accepting SDK licences…"
            yes | "${'$'}ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1 || true
            "${'$'}ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --version
        """.trimIndent()

        val code = runInAlpine(script, null, emptyList(), onLine)
        if (code == 0 && androidSdkInstalled) {
            onLine("Android SDK manager ready. Install components with:", false)
            onLine("  sdkmanager \"platform-tools\" \"platforms;android-34\" \"build-tools;34.0.0\"", false)
            onLine("Tip: install on-device adb/fastboot with: pkg install platform-tools", false)
            true
        } else {
            onLine("Android SDK setup failed (exit $code). Check the connection and retry.", true)
            false
        }
    }

    /**
     * Clones the Flutter SDK (which bundles Dart) from GitHub. `gcompat` lets the
     * glibc-built Dart binaries run on Alpine's musl libc. Editing, analysis and
     * `dart` work on-device; we surface `flutter --version` to confirm.
     */
    suspend fun installFlutter(onLine: (String, Boolean) -> Unit): Boolean = withContext(Dispatchers.IO) {
        if (!alpineInstalled) { onLine("Install the Linux environment first: pkg install alpine", true); return@withContext false }
        onLine("Installing Flutter prerequisites (git, bash, gcompat…)…", false)
        if (!apkAdd("git curl unzip bash gcompat which", onLine)) return@withContext false

        writeProfileScript(
            "upx-flutter.sh",
            "export PATH=/root/flutter/bin:/root/flutter/bin/cache/dart-sdk/bin:\$PATH\n" +
                "export PUB_CACHE=/root/.pub-cache",
        )

        val script = """
            set -e
            cd /root
            export PATH=/root/flutter/bin:${'$'}PATH
            if [ ! -d flutter/.git ]; then
                echo "Cloning Flutter (stable) — this is large (~1 GB), please wait…"
                git config --global --add safe.directory /root/flutter || true
                git clone --depth 1 -b stable https://github.com/flutter/flutter.git
            else
                echo "Flutter already cloned; updating…"
                git -C flutter pull --ff-only || true
            fi
            git config --global --add safe.directory /root/flutter || true
            flutter --version || true
        """.trimIndent()

        val code = runInAlpine(script, null, emptyList(), onLine)
        if (flutterInstalled) {
            onLine("Flutter SDK installed. Try: flutter --version  •  dart --version", false)
            onLine("In a project run: flutter pub get   then   flutter run", false)
            true
        } else {
            onLine("Flutter clone did not complete (exit $code). Retry: pkg install flutter", true)
            false
        }
    }

    /** Downloads the official Gradle binary distribution and puts it on the PATH. */
    suspend fun installGradle(onLine: (String, Boolean) -> Unit): Boolean = withContext(Dispatchers.IO) {
        if (!alpineInstalled) { onLine("Install the Linux environment first: pkg install alpine", true); return@withContext false }
        onLine("Installing Java (required by Gradle)…", false)
        if (!apkAdd("openjdk17 unzip curl", onLine)) return@withContext false

        writeProfileScript("upx-gradle.sh", "export PATH=/opt/gradle/bin:\$PATH")

        val ver = "8.7"
        val script = """
            set -e
            cd /tmp
            echo "Downloading Gradle $ver…"
            curl -L --fail -o gradle.zip "https://services.gradle.org/distributions/gradle-$ver-bin.zip"
            rm -rf /opt/gradle
            mkdir -p /opt
            unzip -q gradle.zip -d /opt
            mv "/opt/gradle-$ver" /opt/gradle
            rm -f gradle.zip
            export PATH=/opt/gradle/bin:${'$'}PATH
            gradle --version
        """.trimIndent()

        val code = runInAlpine(script, null, emptyList(), onLine)
        if (gradleInstalled) {
            onLine("Gradle ready — build Kotlin/Java projects with: gradle build", false)
            true
        } else {
            onLine("Gradle install failed (exit $code).", true)
            false
        }
    }

    // ------------------------------------------------------------------
    // Pure-Kotlin tar.gz extraction. Deliberately not delegated to any
    // downloaded binary: some devices' seccomp filters kill foreign static
    // binaries (SIGSYS), and the Alpine install must not depend on that.
    // Handles dirs, files (with exec bits), symlinks, hard links (copied)
    // and GNU long names — everything in the Alpine mini rootfs.
    // ------------------------------------------------------------------

    private fun extractTarGz(archive: File, dest: File, onLine: (String, Boolean) -> Unit): Boolean {
        return try {
            var entries = 0
            java.util.zip.GZIPInputStream(archive.inputStream().buffered()).use { input ->
                val block = ByteArray(512)
                var longName: String? = null
                while (readFully(input, block)) {
                    if (block.all { it == 0.toByte() }) continue
                    val type = block[156].toInt().toChar()
                    val size = parseOctal(block, 124, 12)
                    val rawName = longName ?: buildString {
                        val prefix = parseName(block, 345, 155)
                        if (prefix.isNotEmpty()) append(prefix).append('/')
                        append(parseName(block, 0, 100))
                    }
                    longName = null
                    // Never let an archive entry escape the destination dir.
                    val safeName = rawName.trimStart('/')
                    if (safeName.isEmpty() || safeName.split('/').contains("..")) {
                        skipData(input, size)
                        continue
                    }
                    val target = File(dest, safeName)
                    when (type) {
                        'L' -> longName = String(readData(input, size), Charsets.UTF_8).trimEnd('\u0000')
                        '5' -> target.mkdirs()
                        '2' -> {
                            target.parentFile?.mkdirs()
                            target.delete()
                            runCatching { Os.symlink(parseName(block, 157, 100), target.absolutePath) }
                        }
                        '1' -> {
                            skipData(input, size)
                            target.parentFile?.mkdirs()
                            val original = File(dest, parseName(block, 157, 100).trimStart('/'))
                            runCatching { original.copyTo(target, overwrite = true) }
                        }
                        '0', '\u0000' -> {
                            target.parentFile?.mkdirs()
                            writeData(input, size, target)
                            val mode = parseOctal(block, 100, 8)
                            if (mode and 0b1001001L != 0L) target.setExecutable(true, false)
                        }
                        else -> skipData(input, size)
                    }
                    entries++
                }
            }
            onLine("Extracted $entries entries.", false)
            true
        } catch (e: Exception) {
            onLine("Extract failed: ${e.message}", true)
            false
        }
    }

    private fun readFully(input: java.io.InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) return false
            off += n
        }
        return true
    }

    private fun readData(input: java.io.InputStream, size: Long): ByteArray {
        val data = ByteArray(size.toInt())
        var off = 0
        while (off < data.size) {
            val n = input.read(data, off, data.size - off)
            if (n < 0) throw java.io.EOFException("archive truncated")
            off += n
        }
        skipBytes(input, padOf(size))
        return data
    }

    private fun writeData(input: java.io.InputStream, size: Long, target: File) {
        target.outputStream().use { out ->
            var remaining = size
            val buf = ByteArray(64 * 1024)
            while (remaining > 0) {
                val n = input.read(buf, 0, minOf(remaining, buf.size.toLong()).toInt())
                if (n < 0) throw java.io.EOFException("archive truncated")
                out.write(buf, 0, n)
                remaining -= n
            }
        }
        skipBytes(input, padOf(size))
    }

    private fun skipData(input: java.io.InputStream, size: Long) = skipBytes(input, size + padOf(size))

    private fun skipBytes(input: java.io.InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val n = input.skip(remaining)
            if (n > 0) { remaining -= n; continue }
            if (input.read() < 0) throw java.io.EOFException("archive truncated")
            remaining--
        }
    }

    private fun padOf(size: Long): Long = (512 - size % 512) % 512

    private fun parseName(block: ByteArray, offset: Int, length: Int): String {
        var end = offset
        while (end < offset + length && block[end] != 0.toByte()) end++
        return String(block, offset, end - offset, Charsets.UTF_8)
    }

    private fun parseOctal(block: ByteArray, offset: Int, length: Int): Long {
        var value = 0L
        for (i in offset until offset + length) {
            val c = block[i].toInt().toChar()
            if (c in '0'..'7') value = value * 8 + (c - '0')
            else if (value > 0) break
        }
        return value
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
