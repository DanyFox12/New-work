package com.upx.builder.project

import android.os.Build
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
class ToolchainManager(
    filesDir: File,
    /** App assets, so toolchain bootstrap files bundled in the APK (under
     *  assets/proot/) can be used before falling back to network downloads. */
    private val assets: android.content.res.AssetManager? = null,
) {

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

    /** Serialises environment installs so concurrent triggers (first-launch
     *  auto-bootstrap + a user command) can never corrupt the rootfs. */
    private val installMutex = Mutex()

    /**
     * True when this APK carries the complete private environment bootstrap for
     * this device's CPU (proot launcher + Alpine base system in the assets —
     * upxBuilder's equivalent of Termux's libtermux-bootstrap, but built from
     * path-independent pieces so it works under any applicationId). The Colab
     * notebook bundles it automatically; without it the app downloads the same
     * files at install time.
     */
    val hasBundledBootstrap: Boolean
        get() {
            val assetManager = assets ?: return false
            val prootArch = androidProotArch() ?: return false
            val rootfsArch = alpineArch() ?: return false
            return runCatching { assetManager.open("proot/proot-android-$prootArch.tar.gz").close() }.isSuccess &&
                runCatching { assetManager.open("alpine/alpine-minirootfs-$rootfsArch.tar.gz").close() }.isSuccess
        }

    /** BusyBox build name for this device's CPU. */
    private fun busyboxVariant(): String? {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return null
        return when {
            abi.startsWith("arm64") -> "busybox-armv8l"
            abi.startsWith("armeabi") -> "busybox-armv7l"
            abi.startsWith("x86_64") -> "busybox-x86_64"
            abi.startsWith("x86") -> "busybox-i686"
            else -> null
        }
    }

    /**
     * Mirrors of statically linked BusyBox builds (musl libc, zero shared-lib
     * dependencies — they run on Android kernels as-is) for this device's CPU.
     * We try each in order so one mirror being down does not break the install.
     */
    private fun busyboxUrls(): List<String> {
        val variant = busyboxVariant() ?: return emptyList()
        return listOf(
            "https://busybox.net/downloads/binaries/1.31.0-defconfig-multiarch-musl/$variant",
            "https://frippery.org/files/busybox/$variant",
        )
    }

    /** Copies the BusyBox bundled in the APK's assets, if this build has one. */
    private fun copyBusyboxFromAsset(onLine: (String, Boolean) -> Unit): Boolean {
        val assetManager = assets ?: return false
        val variant = busyboxVariant() ?: return false
        return try {
            assetManager.open("busybox/$variant").use { input ->
                busybox.outputStream().use { output -> input.copyTo(output) }
            }
            onLine("Using the BusyBox bundled in the app…", false)
            true
        } catch (_: Exception) {
            false // not bundled in this APK build
        }
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
        installMutex.withLock {
        if (busyboxInstalled) {
            onLine("BusyBox is already installed.", false)
            return@withContext true
        }
        // Prefer the copy bundled inside the APK; download only without one.
        var obtained = copyBusyboxFromAsset(onLine)
        if (!obtained) {
            val urls = busyboxUrls()
            if (urls.isEmpty()) {
                onLine("Unsupported CPU: ${Build.SUPPORTED_ABIS.joinToString()}", true)
                return@withContext false
            }
            for (url in urls) {
                if (downloadTo(url, busybox, onLine)) { obtained = true; break }
                onLine("Trying next mirror…", false)
            }
        }
        if (!obtained) {
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
        } // installMutex
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

    // Two proot builds are known:
    //  1. The Android-native build (green-green-avk/build-proot-android): compiled
    //     with the NDK against bionic (interpreter /system/bin/linker64), with its
    //     loader binaries separate. This is what Android terminal apps use and it
    //     runs on devices whose seccomp filters kill generic Linux builds.
    //  2. The generic musl-static build from proot-me — kept as a fallback.
    // Each candidate is PROBED (it must actually start a shell in Alpine) before
    // the environment is marked installed, and the working choice is persisted.

    /** Where the Android-native proot package is unpacked. */
    private val prootDist = File(prefix, "proot-dist")
    private val distProot = File(prootDist, "root/bin/proot")
    private val distLoader = File(prootDist, "root/libexec/proot/loader")
    private val distLoader32 = File(prootDist, "root/libexec/proot/loader32")

    /** The generic static proot (fallback) lives here. */
    private val staticProot = File(bin, "proot")

    /** Holds the absolute path of the proot binary that passed the probe. */
    private val prootChoiceMarker = File(prefix, "etc/proot-binary")

    /** Marker written when this device's proot supports --link2symlink
     *  (needed because app storage forbids hard links, which apk creates). */
    private val link2symlinkMarker = File(prefix, "etc/proot-link2symlink")

    /** Present only after a probe confirmed the environment actually starts.
     *  Without this, a downloaded-but-broken proot must NOT count as installed. */
    private val verifiedMarker = File(prefix, "etc/alpine-verified")

    /** The proot binary verified to work on this device (marker first). */
    private fun chosenProot(): File? {
        val fromMarker = runCatching { prootChoiceMarker.readText().trim() }.getOrNull()
            ?.let { File(it) }?.takeIf { it.canExecute() }
        return fromMarker ?: listOf(distProot, staticProot).firstOrNull { it.canExecute() }
    }

    val alpineInstalled: Boolean
        get() = chosenProot() != null &&
            File(alpineRoot, "etc/alpine-release").exists() &&
            verifiedMarker.exists()

    /** Architecture name used by the Android-native proot packages. */
    private fun androidProotArch(): String? {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return null
        return when {
            abi.startsWith("arm64") -> "aarch64"
            abi.startsWith("armeabi") -> "armv7a"
            abi.startsWith("x86_64") -> "x86_64"
            abi.startsWith("x86") -> "i686"
            else -> null
        }
    }

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

    /** Unpacks an Android-native proot package into [prootDist]. */
    private fun unpackProotDist(tarball: File, onLine: (String, Boolean) -> Unit): Boolean {
        prootDist.deleteRecursively()
        prootDist.mkdirs()
        val ok = extractTarGz(tarball, prootDist, onLine)
        tarball.delete()
        if (!ok || !distProot.exists()) return false
        distProot.setExecutable(true, false)
        if (distLoader.exists()) distLoader.setExecutable(true, false)
        if (distLoader32.exists()) distLoader32.setExecutable(true, false)
        return true
    }

    /** Installs the Android-native proot bundled in the APK's assets, if present. */
    private fun installAndroidProotFromAsset(onLine: (String, Boolean) -> Unit): Boolean {
        val assetManager = assets ?: return false
        val arch = androidProotArch() ?: return false
        val tarball = File(tmp, "proot-android.tar.gz")
        try {
            assetManager.open("proot/proot-android-$arch.tar.gz").use { input ->
                tarball.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (_: Exception) {
            return false // not bundled in this APK build
        }
        onLine("Using the Android proot launcher bundled in the app…", false)
        return unpackProotDist(tarball, onLine)
    }

    /** Downloads the Android-native proot package from the upstream project. */
    private fun installAndroidProotFromUrl(onLine: (String, Boolean) -> Unit): Boolean {
        val arch = androidProotArch() ?: return false
        val tarball = File(tmp, "proot-android.tar.gz")
        val url = "https://raw.githubusercontent.com/green-green-avk/build-proot-android/master/packages/proot-android-$arch.tar.gz"
        if (!downloadTo(url, tarball, onLine)) return false
        return unpackProotDist(tarball, onLine)
    }

    /** Downloads the generic static proot (fallback for unusual setups). */
    private fun installStaticProot(onLine: (String, Boolean) -> Unit): Boolean {
        if (staticProot.canExecute()) return true
        val purl = prootUrl()
        if (purl == null) {
            onLine("Unsupported CPU: ${Build.SUPPORTED_ABIS.joinToString()}", true)
            return false
        }
        if (!downloadTo(purl, staticProot, onLine)) return false
        if (!isElf(staticProot)) {
            onLine("proot download is not a valid executable.", true)
            staticProot.delete()
            return false
        }
        staticProot.setExecutable(true, false)
        return true
    }

    /**
     * Tries every proot candidate until one actually starts a shell inside
     * Alpine on THIS device, persisting the working choice. Failures are
     * reported with the real reason (signal/exit/output) instead of a generic
     * "could not start" so problems are diagnosable from a screenshot.
     */
    private fun verifyProot(onLine: (String, Boolean) -> Unit): Boolean {
        File(prefix, "etc").mkdirs()
        verifiedMarker.delete()

        var androidDistReady = false
        val candidates = listOf<Triple<String, () -> Boolean, File>>(
            Triple("Android proot (bundled)", {
                installAndroidProotFromAsset(onLine).also { androidDistReady = it }
            }, distProot),
            Triple("Android proot (online)", {
                // Same content as the bundled package — skip if that already failed.
                if (androidDistReady) false else installAndroidProotFromUrl(onLine)
            }, distProot),
            Triple("generic static proot", { installStaticProot(onLine) }, staticProot),
        )

        for ((label, install, binary) in candidates) {
            if (!runCatching(install).getOrDefault(false)) continue
            for (link2symlink in listOf(true, false)) {
                val (ok, why) = probeProot(binary, link2symlink)
                if (ok) {
                    prootChoiceMarker.writeText(binary.absolutePath)
                    if (link2symlink) link2symlinkMarker.writeText("1") else link2symlinkMarker.delete()
                    verifiedMarker.writeText("1")
                    onLine("Launcher verified: $label.", false)
                    return true
                }
                onLine("  ✗ $label${if (link2symlink) " [link2symlink]" else ""}: $why", true)
            }
        }
        onLine("No proot build could start on this device.", true)
        onLine("Please report your device model + Android version (the lines above show why each attempt failed).", true)
        return false
    }

    /** Runs `echo` inside Alpine with [binary]; returns success + failure detail.
     *  A watchdog thread kills the probe after 30 s so a hanging proot can never
     *  freeze an install (Process.waitFor(timeout) needs API 26; minSdk is 24). */
    private fun probeProot(binary: File, link2symlink: Boolean): Pair<Boolean, String> = try {
        val p = ProcessBuilder(prootArgv(binary, link2symlink, null, emptyList(), "echo upx-ok"))
            .redirectErrorStream(true)
            .apply { environment().putAll(prootEnv(binary)) }
            .start()
        var timedOut = false
        val watchdog = Thread {
            try {
                Thread.sleep(30_000)
                timedOut = true
                p.destroy()
            } catch (_: InterruptedException) {
            }
        }.apply { isDaemon = true; start() }
        val out = p.inputStream.bufferedReader().readText()
        val code = p.waitFor()
        watchdog.interrupt()
        when {
            timedOut -> false to "timed out after 30 s"
            code == 0 && out.contains("upx-ok") -> true to ""
            else -> false to describeFailure(code, out)
        }
    } catch (e: Exception) {
        false to (e.message ?: "failed to start the process")
    }

    private fun describeFailure(code: Int, out: String): String {
        val sig = code - 128
        val why = when {
            sig == 31 -> "killed by SIGSYS — this device's seccomp filter blocked the binary"
            sig == 9 -> "killed (SIGKILL — out of memory or stopped by the system)"
            sig == 11 -> "crashed (SIGSEGV)"
            code > 128 -> "killed by signal $sig"
            else -> "exit $code"
        }
        val tail = out.trim().lines().lastOrNull { it.isNotBlank() }.orEmpty()
        return if (tail.isEmpty()) why else "$why — $tail"
    }

    /** Alpine's architecture name for this device. */
    private fun alpineArch(): String? {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return null
        return when {
            abi.startsWith("arm64") -> "aarch64"
            abi.startsWith("armeabi") -> "armv7"
            abi.startsWith("x86_64") -> "x86_64"
            abi.startsWith("x86") -> "x86"
            else -> null
        }
    }

    private fun alpineUrls(): List<String> {
        val arch = alpineArch() ?: return emptyList()
        val release = "3.20.3"
        val path = "alpine/v3.20/releases/$arch/alpine-minirootfs-$release-$arch.tar.gz"
        return listOf(
            "https://dl-cdn.alpinelinux.org/$path",
            "https://uk.alpinelinux.org/$path",
            "https://mirror.leaseweb.com/$path",
        )
    }

    /** Copies the Alpine base system bundled in the APK's assets (when the APK
     *  was built with it — see the Colab notebook), AndroidIDE-bootstrap-style,
     *  so the whole environment installs with zero network. */
    private fun copyAlpineFromAsset(dest: File, onLine: (String, Boolean) -> Unit): Boolean {
        val assetManager = assets ?: return false
        val arch = alpineArch() ?: return false
        return try {
            assetManager.open("alpine/alpine-minirootfs-$arch.tar.gz").use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            onLine("Using the Linux base system bundled in the app…", false)
            true
        } catch (_: Exception) {
            false // not bundled in this APK build
        }
    }

    /**
     * Installs the Alpine environment: downloads the Alpine mini root filesystem
     * if it is not already on the device, unpacks it with the built-in
     * pure-Kotlin extractor, configures DNS and apk repositories, then finds a
     * proot build that ACTUALLY starts on this device (bundled Android build →
     * downloaded Android build → generic static build) before declaring success.
     */
    suspend fun installAlpine(onLine: (String, Boolean) -> Unit): Boolean = withContext(Dispatchers.IO) {
        installMutex.withLock {
        if (alpineInstalled) {
            onLine("Alpine Linux is already installed and verified.", false)
            return@withContext true
        }

        if (!File(alpineRoot, "etc/alpine-release").exists()) {
            val tarball = File(tmp, "alpine-minirootfs.tar.gz")
            // Prefer the copy bundled inside the APK; download only without one.
            var obtained = copyAlpineFromAsset(tarball, onLine)
            if (!obtained) {
                for (url in alpineUrls()) {
                    if (downloadTo(url, tarball, onLine)) { obtained = true; break }
                    onLine("Trying next mirror…", false)
                }
            }
            if (!obtained) {
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
        } else {
            onLine("Linux files already on the device — checking the launcher…", false)
        }

        // DNS + package repositories (main + community = thousands of packages).
        File(alpineRoot, "etc/resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        File(alpineRoot, "etc/apk/repositories").writeText(
            "https://dl-cdn.alpinelinux.org/alpine/v3.20/main\n" +
                "https://dl-cdn.alpinelinux.org/alpine/v3.20/community\n"
        )

        if (!verifyProot(onLine)) return@withContext false

        onLine("Alpine Linux ${File(alpineRoot, "etc/alpine-release").readText().trim()} installed!", false)
        onLine("Install real tools now, e.g.: pkg install python java cmake — or open Setup.", false)
        true
        } // installMutex
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
        val binary = chosenProot()
        if (binary == null) {
            onLine("The Linux launcher is missing — run: pkg install alpine", true)
            -1
        } else {
            val p = ProcessBuilder(prootArgv(binary, link2symlinkMarker.exists(), workDir, binds, command))
                .redirectErrorStream(true)
                .apply { environment().putAll(prootEnv(binary)) }
                .start()
            p.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { onLine(it, false) }
            }
            p.waitFor()
        }
    } catch (e: Exception) {
        onLine("alpine error: ${e.message}", true)
        -1
    }

    private fun prootArgv(
        binary: File,
        link2symlink: Boolean,
        workDir: File?,
        binds: List<File>,
        command: String,
    ): List<String> {
        val argv = mutableListOf(binary.absolutePath, "-r", alpineRoot.absolutePath)
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

    private fun prootEnv(binary: File): Map<String, String> {
        val env = mutableMapOf(
            // proot keeps its runtime state here; the default /tmp does not exist.
            "PROOT_TMP_DIR" to tmp.absolutePath,
            // Avoids seccomp issues on several Android versions (Termux does the same).
            "PROOT_NO_SECCOMP" to "1",
        )
        // The Android-native build ships its loaders as separate files and finds
        // them through these variables (the static build embeds its loader).
        if (binary == distProot) {
            if (distLoader.exists()) env["PROOT_LOADER"] = distLoader.absolutePath
            if (distLoader32.exists()) env["PROOT_LOADER_32"] = distLoader32.absolutePath
        }
        return env
    }

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

    /** apk add helper that streams progress to [onLine]; returns true on success.
     *
     *  Alpine's busybox post-install trigger dies under proot ("fchdir: Function
     *  not implemented") even though the packages install fine. We do the
     *  trigger's job ourselves — `busybox --install -s` relinks every applet —
     *  and replace the scary-but-harmless ERROR lines with one clear note.
     *  Because apk counts that dead trigger as an error in its exit code, success
     *  is judged by the actual install state (`apk info -e` per package), never
     *  by apk's exit code alone. */
    fun apkAdd(packages: String, onLine: (String, Boolean) -> Unit): Boolean {
        onLine("apk add $packages", false)
        var triggerNoise = false
        val script =
            "apk update >/dev/null 2>&1; apk add --no-cache $packages; rc=\$?; " +
                "/bin/busybox --install -s >/dev/null 2>&1 || true; " +
                "missing=0; for p in $packages; do apk info -e \"\$p\" >/dev/null 2>&1 || missing=1; done; " +
                "if [ \$missing -eq 0 ]; then exit 0; fi; exit \$rc"
        val code = runInAlpine(script, null, emptyList()) { line, isError ->
            if (line.contains("busybox-") && line.contains(".trigger")) triggerNoise = true
            else onLine(line, isError)
        }
        if (triggerNoise) {
            onLine("(busybox trigger skipped — a harmless proot limitation; commands were relinked automatically)", false)
        }
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
