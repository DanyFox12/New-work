package com.upx.builder.project

/**
 * How a [Toolchain] is installed once the Alpine Linux environment is ready.
 *
 *  - [Apk]        a plain `apk add …` of arch-native Alpine packages (these run
 *                 natively on the device's CPU — the reliable common case).
 *  - [AndroidSdk] downloads Google's command-line tools + sdkmanager (Java, so
 *                 CPU-independent) and accepts the SDK licences.
 *  - [Flutter]    clones the Flutter SDK (bundles Dart) from GitHub.
 *  - [Gradle]     downloads the official Gradle binary distribution.
 *  - [Busybox] / [Alpine] bootstrap the environment itself (host-side).
 */
sealed interface InstallMethod {
    data object Busybox : InstallMethod
    data object Alpine : InstallMethod
    data class Apk(val packages: String) : InstallMethod
    data object AndroidSdk : InstallMethod
    data object Flutter : InstallMethod
    data object Gradle : InstallMethod
}

/** Groups the toolchains in the Setup guide, like sections in an SDK manager. */
enum class ToolCategory { CORE, LANGUAGE, BUILD, ANDROID, UTIL }

/**
 * One installable developer tool/toolchain. The same registry powers both the
 * terminal's `pkg install <id>` command and the in-app Setup guide, so the two
 * can never drift apart.
 */
data class Toolchain(
    /** Canonical name the user types: `pkg install <id>`. */
    val id: String,
    val displayName: String,
    /** One-line "what it is / what it's for". */
    val summary: String,
    /** Commands/runtimes it provides on the PATH afterwards. */
    val provides: String,
    val category: ToolCategory,
    val method: InstallMethod,
    /** Rough download size, shown so users on mobile data know what to expect. */
    val sizeHint: String = "",
    /** Extra friendly names that also resolve to this toolchain. */
    val aliases: List<String> = emptyList(),
    /** Honest caveat shown in the guide (empty if none). */
    val note: String = "",
) {
    /** The exact terminal command that installs it. */
    val command: String get() = "pkg install $id"
}

/**
 * The catalogue of everything upxBuilder can set up on-device. It is deliberately
 * honest about what runs natively on an Android CPU (compilers, Python, Java,
 * Node, Go, adb) versus what is best-effort (Flutter, the Android build-tools),
 * so users are never surprised.
 */
object Toolchains {

    val all: List<Toolchain> = listOf(
        // ---- Core environment -------------------------------------------------
        Toolchain(
            id = "alpine",
            displayName = "Linux environment",
            summary = "The full Linux userland (Alpine) that every real tool runs inside, via proot — exactly like Termux.",
            provides = "sh, apk package manager, /root home",
            category = ToolCategory.CORE,
            method = InstallMethod.Alpine,
            sizeHint = "~4 MB",
        ),
        Toolchain(
            id = "busybox",
            displayName = "BusyBox core utils",
            summary = "300+ classic Unix commands in one tiny static binary — works even before Alpine is installed.",
            provides = "ls, cat, grep, sed, tar, wget, vi…",
            category = ToolCategory.CORE,
            method = InstallMethod.Busybox,
            sizeHint = "~1 MB",
        ),

        // ---- Languages & runtimes --------------------------------------------
        Toolchain(
            id = "python",
            displayName = "Python 3",
            summary = "The Python interpreter and pip, for scripts, data work and tooling.",
            provides = "python3, pip3",
            category = ToolCategory.LANGUAGE,
            method = InstallMethod.Apk("python3 py3-pip"),
            sizeHint = "~60 MB",
            aliases = listOf("python3", "pip", "pip3"),
        ),
        Toolchain(
            id = "java",
            displayName = "Java JDK (OpenJDK 17)",
            summary = "Compile and run Java on-device. Also required by Gradle and the Android SDK manager.",
            provides = "javac, java, jar, keytool",
            category = ToolCategory.LANGUAGE,
            method = InstallMethod.Apk("openjdk17"),
            sizeHint = "~120 MB",
            aliases = listOf("jdk", "javac", "openjdk", "openjdk17"),
        ),
        Toolchain(
            id = "node",
            displayName = "Node.js",
            summary = "JavaScript/TypeScript runtime with the npm package manager.",
            provides = "node, npm, npx",
            category = ToolCategory.LANGUAGE,
            method = InstallMethod.Apk("nodejs npm"),
            sizeHint = "~25 MB",
            aliases = listOf("nodejs", "npm"),
        ),
        Toolchain(
            id = "go",
            displayName = "Go",
            summary = "The Go compiler and toolchain for building fast static binaries.",
            provides = "go, gofmt",
            category = ToolCategory.LANGUAGE,
            method = InstallMethod.Apk("go"),
            sizeHint = "~120 MB",
            aliases = listOf("golang"),
        ),

        // ---- Build tools ------------------------------------------------------
        Toolchain(
            id = "gcc",
            displayName = "C / C++ compilers",
            summary = "The GNU build essentials: compile C and C++ natively on the device.",
            provides = "gcc, g++, make, ld",
            category = ToolCategory.BUILD,
            method = InstallMethod.Apk("build-base"),
            sizeHint = "~180 MB",
            aliases = listOf("g++", "build-base", "build-essential"),
        ),
        Toolchain(
            id = "clang",
            displayName = "Clang / LLVM",
            summary = "The LLVM C/C++/Objective-C compiler — a modern alternative to gcc.",
            provides = "clang, clang++, lld",
            category = ToolCategory.BUILD,
            method = InstallMethod.Apk("clang"),
            sizeHint = "~90 MB",
            aliases = listOf("llvm"),
        ),
        Toolchain(
            id = "cmake",
            displayName = "CMake",
            summary = "The build-system generator used by the C++ project template (plus make).",
            provides = "cmake, ctest, make",
            category = ToolCategory.BUILD,
            method = InstallMethod.Apk("cmake make"),
            sizeHint = "~40 MB",
        ),
        Toolchain(
            id = "gradle",
            displayName = "Gradle",
            summary = "The build tool for Kotlin/Java/Android projects. Needs the Java JDK.",
            provides = "gradle",
            category = ToolCategory.BUILD,
            method = InstallMethod.Gradle,
            sizeHint = "~130 MB",
            aliases = listOf("kotlin"),
            note = "Builds Kotlin and Java projects. Pulls in OpenJDK 17 automatically.",
        ),

        // ---- Android & mobile -------------------------------------------------
        Toolchain(
            id = "sdk",
            displayName = "Android SDK (command-line)",
            summary = "Google's sdkmanager + command-line tools, to download platforms and build-tools.",
            provides = "sdkmanager, avdmanager",
            category = ToolCategory.ANDROID,
            method = InstallMethod.AndroidSdk,
            sizeHint = "~150 MB",
            aliases = listOf("android-sdk", "sdkmanager", "cmdline-tools"),
            note = "sdkmanager is Java, so it runs anywhere. The native build-tools it downloads (aapt2, d8) are x86_64 — full on-device APK builds still need an arm-native toolchain.",
        ),
        Toolchain(
            id = "platform-tools",
            displayName = "Platform tools (adb & fastboot)",
            summary = "adb and fastboot, built natively for your device's CPU via Alpine's android-tools.",
            provides = "adb, fastboot",
            category = ToolCategory.ANDROID,
            method = InstallMethod.Apk("android-tools"),
            sizeHint = "~15 MB",
            aliases = listOf("adb", "fastboot", "android-tools"),
        ),
        Toolchain(
            id = "flutter",
            displayName = "Flutter SDK (Dart)",
            summary = "Clones the Flutter SDK (which bundles Dart) so you can develop and run Flutter projects.",
            provides = "flutter, dart",
            category = ToolCategory.ANDROID,
            method = InstallMethod.Flutter,
            sizeHint = "~1 GB",
            aliases = listOf("dart"),
            note = "Editing, `dart` and analysis work on-device. Full `flutter build apk` also needs the Android SDK and an arm-native build-tools set — this clones the SDK so you're ready when running on a host.",
        ),

        // ---- Utilities --------------------------------------------------------
        Toolchain(
            id = "git",
            displayName = "Git",
            summary = "Clone, commit and push repositories right from the device.",
            provides = "git",
            category = ToolCategory.UTIL,
            method = InstallMethod.Apk("git"),
            sizeHint = "~25 MB",
        ),
    )

    /** Tools installed by `pkg install all` — the common native dev set (no
     *  multi-hundred-MB Android SDK / Flutter, which users add deliberately). */
    val coreSetup: List<String> = listOf("gcc", "cmake", "python", "java", "node", "go", "git")

    fun byId(query: String): Toolchain? {
        val q = query.trim().lowercase()
        return all.firstOrNull { it.id == q || q in it.aliases }
    }

    fun byCategory(category: ToolCategory): List<Toolchain> = all.filter { it.category == category }
}
