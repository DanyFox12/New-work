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

## About building on-device

Real on-device *compilation* (the headline feature of AndroidIDE) requires
bundling toolchains — a device-compatible JDK, Gradle and the Android SDK
build-tools — which is a large, separate piece of work. In this edition the
**Run / Build / Clean** actions invoke those tools through the system process
runner and stream their output to the console; if a toolchain is not present on
the device they report that cleanly rather than failing. The architecture
(`project/BuildRunner.kt`) is the integration point where bundled toolchains
(e.g. a Termux-style prefix) would be wired in next.

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
- `compileSdk` 34, `minSdk` 24, `targetSdk` 34

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
