# upxBuilder for Android

The **Android edition** of upxBuilder — the same beautiful, Android-Studio-style
IDE design, running as a native app **on your Android device** (similar in spirit
to AndroidIDE). Built with **Kotlin** and **Jetpack Compose**.

It shares upxBuilder's core with the desktop edition: the 10 themes, the
4 UI languages (English, العربية with right-to-left layout, 中文, Italiano),
the syntax highlighter for **Dart/Flutter, C++, Java and Kotlin**, the project
templates and the in-app coding guides.

## What works on the device

- ✅ **Full IDE UI** — adaptive toolbar (with overflow menu on narrow screens),
  project explorer, editor tabs, line-numbered code editor with live syntax
  highlighting, console, problems panel and status bar. The layout adapts to any
  screen size and orientation, and rotation does not lose your work.
- ✅ **7 language templates** — Flutter (Dart), C++, Java, Kotlin, Python,
  JavaScript and Go — created in the app's own storage and edited on the phone.
- ✅ **Native C++ code analyzer** — a fast NDK-built engine (`libupxanalyzer.so`)
  scans your code as you type and reports problems (unbalanced brackets,
  unterminated strings, TODO/FIXME, overlong lines) in the Problems panel, with
  live error/warning counts in the status bar.
- ✅ **Smart editing** — auto-indent on Enter and auto-closing brackets.
- ✅ **10 themes & 4 UI languages**, persisted between launches.
- ✅ **Hardened release build** — R8 obfuscation + code/resource shrinking, with
  signing supplied via environment variables so secrets never enter the repo.

## The terminal and on-device tools

upxBuilder has a **real terminal** (TERMINAL tab) and a built-in package manager,
`pkg`, modelled on Termux's (`project/ToolchainManager.kt`, `project/Toolchains.kt`).
The app deliberately targets **SDK 28** because Android only allows executing
app-private binaries at that level — the same technique Termux and AndroidIDE
use (fine for sideloading; not allowed on the Play Store).

Two layers provide the tools:

1. **BusyBox** — `pkg install busybox` downloads a static BusyBox for the device
   CPU into `$PREFIX/bin`, runs it, and links 300+ real Unix commands (ls, grep,
   vi, tar, wget, unzip, …). Works even before the Linux environment is set up.
2. **A full Linux userland** — `pkg install alpine` unpacks a tiny Alpine Linux
   root filesystem and runs it under **proot** (no root needed). Its package
   manager, `apk`, then installs thousands of **arch-native** packages that run
   directly on the device's CPU. The terminal becomes an Alpine shell, with `cd`
   persisting between commands, exactly like Termux.

### Installing toolchains (SDK, JDK, platform-tools, CMake, Python, Flutter…)

Tap **Setup** (the download icon in the toolbar, or the button on the welcome
screen) for a one-tap installer, or type the commands in the terminal. The first
install also sets up the Linux environment automatically.

| Tool | Command | What you get |
| --- | --- | --- |
| Linux env | `pkg install alpine` | the full userland everything runs in |
| C/C++ | `pkg install gcc` / `pkg install clang` | gcc, g++, make / clang, lld |
| CMake | `pkg install cmake` | cmake, ctest, make |
| Python | `pkg install python` | python3, pip3 |
| Java JDK | `pkg install java` | javac, java, jar, keytool (OpenJDK 17) |
| Node.js | `pkg install node` | node, npm, npx |
| Go | `pkg install go` | go, gofmt |
| Gradle | `pkg install gradle` | gradle (Kotlin/Java builds) |
| Git | `pkg install git` | git |
| Android SDK | `pkg install sdk` | sdkmanager + command-line tools |
| adb/fastboot | `pkg install platform-tools` | arch-native adb & fastboot |
| Flutter | `pkg install flutter` | flutter + Dart SDK |
| The lot | `pkg install all` | the common native dev set in one go |

`pkg help`, `pkg search <name>`, `pkg list` and `pkg uninstall <name>` work too,
and any unknown name falls through to `apk add`, so the entire Alpine repository
is available. Installed tools persist on the PATH (via `/etc/profile.d`), so the
**Build/Run** buttons and later terminal commands find them automatically.

**Honest limits.** `sdkmanager`, `dart`, the compilers, Python, Node, Go and adb
are arch-native and run on the device. A full `flutter build apk` / Android APK
build additionally needs arm-native `aapt2`/`d8` build-tools — Google ships those
only for x86_64 — so on-device APK assembly still depends on an arm-built
build-tools set (the problem AndroidIDE solves with its own tooling). Everything
short of that — editing, analysis, compiling and running Dart/C++/Java/Python/
Node/Go — works on the phone.

## Requirements

- **Android Studio** (Koala / 2024.1+) or a machine with the **Android SDK**.
- Internet on first build (downloads AGP, Compose and AndroidX from Google's
  Maven and Maven Central).

## Build & run

Open the `upxBuilder-android/` folder in Android Studio and press **Run**, or
from the command line with the SDK installed:

```bash
cd upxBuilder-android
# point Gradle at your SDK (or set ANDROID_HOME)
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug      # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug       # installs onto a connected device/emulator
```

> Note: this APK could not be compiled in the cloud environment it was authored
> in (no Android SDK there, and Google's Maven was network-blocked). The project
> is structured to build with a standard Android Studio / SDK setup.

## Tech stack & versions

- Kotlin **1.9.24**, Android Gradle Plugin **8.5.2**, Gradle **8.7**, NDK **26.1** + CMake **3.22** (for the C++ analyzer)
- Jetpack Compose via **Compose BOM 2024.06.00** (Compose compiler ext **1.5.14**)
- Material 3, `material-icons-extended`
- `compileSdk` 34, `minSdk` 24, `targetSdk` **28** (load-bearing: see the terminal section)

## Project structure

```
upxBuilder-android/
├── settings.gradle.kts
├── build.gradle.kts
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/upx/builder/
        │   ├── MainActivity.kt        # Android entry point (setContent)
        │   ├── app/AppState.kt        # state + SharedPreferences persistence
        │   ├── i18n/ theme/ editor/ project/   # shared core
        │   └── ui/                    # Jetpack Compose UI
        └── res/                       # icon, theme, strings
```

The `ui/`, `theme/`, `i18n/`, `editor/` and `project/` packages mirror the
desktop edition; only `MainActivity` and `AppState` (persistence + storage) are
platform-specific.
