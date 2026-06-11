# New-work → upxBuilder

This repository contains **upxBuilder**, a beautiful multi-language IDE built
from scratch to the brief in this repo: an Android-Studio-style application for
writing and building **Flutter (Dart)**, **C++**, **Java** and **Kotlin**, with
a friendly interface, **10 themes**, **4 UI languages** (Arabic, English,
Chinese, Italian) and built-in coding instructions.

It is written in **Kotlin** and comes in two editions that share the same core
(themes, languages, syntax highlighter, templates):

| Edition | Folder | Stack | Status |
| --- | --- | --- | --- |
| **Desktop** | [`upxBuilder/`](upxBuilder/) | Compose Multiplatform for Desktop | ✅ Compiles & runs (screenshots in `upxBuilder/docs/`) |
| **Android** (mobile IDE, like AndroidIDE) | [`upxBuilder-android/`](upxBuilder-android/) | Jetpack Compose + Android Gradle Plugin | ✅ Full project; build it in Android Studio |

See each folder's `README.md` for details, screenshots and how to run it.

## Develop on the device itself (Android edition)

The Android edition is a full mobile IDE *and* a Termux-style development
environment. It ships a **real terminal** and a built-in `pkg` package manager,
plus a one-tap **Setup** screen, so you can install a complete toolchain on the
phone and build right there:

```text
pkg install alpine            # one-time: the full Linux environment (proot)
pkg install python java cmake clang   # native compilers & runtimes
pkg install sdk               # Android command-line tools (sdkmanager)
pkg install platform-tools    # adb & fastboot, built for your device's CPU
pkg install flutter           # Flutter + Dart SDK
pkg install all               # the common native dev set in one go
pkg help                      # full command reference
```

Installed tools stay on the PATH, so the **Build** and **Run** buttons use them
automatically. Full details, the complete tool table and the honest limits of
on-device APK builds are in [`upxBuilder-android/README.md`](upxBuilder-android/README.md#the-terminal-and-on-device-tools).

## Quick start

Desktop:

```bash
cd upxBuilder
./gradlew run
```

Android (needs the Android SDK / Android Studio):

```bash
cd upxBuilder-android
./gradlew assembleDebug
```

## What was delivered against the brief

| Request | Delivered |
| --- | --- |
| Build apps; write Flutter, C++, Java, Kotlin | Editor + build runner for all four languages |
| Beautiful sections, user-friendly UI like Android Studio | Toolbar, project explorer, editor tabs, console, status bar |
| Arabic, English, Chinese, Italian | Full i18n with right-to-left layout for Arabic |
| Instructions for writing code | Per-language coding guides in the New Project dialog and Help |
| Attractive colours | Themed UI + syntax highlighting |
| In my name, upxBuilder | Named throughout the app and build |
| 10 themes | Darcula, Ocean, Dracula, Monokai, Nord, Solarized Dark, Synthwave, IntelliJ Light, Solarized Light, Sunset |
| Use Kotlin, C++, etc. | Kotlin + Compose Desktop core |
