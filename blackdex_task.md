# Task: Build a BlackDex-Style DEX Dumper for Android

## Context & Background

We are doing **defensive Android security research** — analyzing a potentially malicious APK that uses **dpt-shell** (open-source Android packer) to hide its real DEX code. The stub `classes.dex` only contains a loader; the real code is decrypted into memory at runtime by `libdpt.so`.

**Frida-based approaches failed** because the app detects Frida via native code before any Java hooks can fire (`pc=0x0`, `lr=0x0` crash — deliberate null-pointer jump from native anti-debug).

The goal is to build a **standalone Android application** (APK) that acts like BlackDex — dumping real DEX files from packed apps **without injecting into the target process**, instead using ART internals and reflection from a privileged root context.

---

## What We Know About the Target

- **Packer**: dpt-shell (`com.luoyesiqiu.shell`)
- **Stub classes**: `ProxyApplication`, `JniBridge`, `ProxyComponentFactory`
- **Native lib**: `libdpt.so` — decrypts real DEX into memory
- **Encrypted assets**: stored in `assets/vwwwwwvwww/`
- **Target package**: `com.hul.shikhar.rssm`
- **Device**: arm64, Android 10 (API 29), rooted

---

## Project Requirements

### Language & Stack
- **Language**: Kotlin (primary) + C++ via JNI where needed
- **Min SDK**: 26 (Android 8)
- **Target SDK**: 34
- **Build**: Gradle with AGP 8.x
- **Root**: Uses root shell commands via `libsu` or direct `su -c`

### Core Functionality

#### 1. DEX Dump Engine
Build a dump engine with **three independent strategies** — whichever succeeds first wins:

**Strategy A — DexClassLoader Reflection**
```
Load target APK via DexClassLoader in our own process
→ Walk BaseDexClassLoader.pathList.dexElements
→ For each element: read dexFile, get mCookie or file path
→ Extract DEX bytes
```

**Strategy B — ART Memory Scan**
```
After loading target APK:
→ Open /proc/self/maps
→ Find anonymous or apk-related memory regions
→ Scan for DEX magic bytes "dex\n035" through "dex\n039"
→ Read declared file_size from DEX header offset 32
→ Save region as .dex file
```

**Strategy C — Direct File Extraction**
```
Copy target APK to our working dir
→ Unzip APK
→ Find assets/vwwwwwvwww/ or similar packed DEX dirs
→ If encrypted, trigger load first then scan memory
→ Also check /data/data/<pkg>/app_dex/ and /data/data/<pkg>/files/
```

#### 2. Anti-Detection Bypass
When loading the target APK in our process, intercept:
- `Debug.isDebuggerConnected()` → return `false`
- `System.exit()` → no-op
- Native `exit()` / `abort()` via JNI hook if possible
- `ActivityManager.getRunningAppProcesses()` → filter out our app

#### 3. Output
- Save each unique DEX to `/sdcard/dex_dump/<package>/<timestamp>/dump_N_<size>.dex`
- Generate a JSON report: `report.json` with:
  - package name
  - packer detected (dpt-shell / jiagu / unknown)
  - number of DEX files found
  - file paths and sizes
  - SHA-256 of each DEX
  - timestamp

#### 4. UI
Simple single-screen UI:
- **Input**: text field for package name (or pick installed app)
- **Button**: "Dump DEX"
- **Output**: scrollable log view (real-time)
- **Status**: success/fail with file paths
- No fancy design needed — functional is enough

---

## Project Structure

```
dex-dumper/
├── app/
│   ├── src/main/
│   │   ├── java/com/security/dexdumper/
│   │   │   ├── MainActivity.kt
│   │   │   ├── DumpEngine.kt          ← core dump logic
│   │   │   ├── StrategyA.kt           ← DexClassLoader reflection
│   │   │   ├── StrategyB.kt           ← memory scan
│   │   │   ├── StrategyC.kt           ← file extraction
│   │   │   ├── AntiBypasses.kt        ← anti-detection hooks
│   │   │   ├── DexVerifier.kt         ← validate DEX magic + header
│   │   │   ├── ReportGenerator.kt     ← JSON report
│   │   │   └── RootShell.kt           ← su command wrapper
│   │   ├── cpp/
│   │   │   └── native_bypass.cpp      ← native exit/abort hooks
│   │   └── res/layout/
│   │       └── activity_main.xml
│   └── build.gradle
└── build.gradle
```

---

## Key Implementation Details

### DEX Header Parsing
```kotlin
// DEX magic at offset 0: "dex\n035\0" through "dex\n039\0"
val DEX_MAGIC = byteArrayOf(0x64, 0x65, 0x78, 0x0a) // "dex\n"

// file_size is at offset 32 (little-endian uint32)
fun readDexSize(bytes: ByteArray, offset: Int): Int {
    return ByteBuffer.wrap(bytes, offset + 32, 4)
        .order(ByteOrder.LITTLE_ENDIAN)
        .int
}
```

### DexPathList Reflection (Strategy A)
```kotlin
// BaseDexClassLoader -> pathList -> dexElements -> dexFile
val baseDexClass = Class.forName("dalvik.system.BaseDexClassLoader")
val pathListField = baseDexClass.getDeclaredField("pathList")
pathListField.isAccessible = true
val pathList = pathListField.get(classLoader)

val dplClass = Class.forName("dalvik.system.DexPathList")
val elementsField = dplClass.getDeclaredField("dexElements")
elementsField.isAccessible = true
val elements = elementsField.get(pathList) as Array<*>
```

### Memory Map Scanning (Strategy B)
```kotlin
// Read /proc/self/maps and find DEX regions
File("/proc/self/maps").readLines().forEach { line ->
    // Parse: start-end perms offset dev inode pathname
    val parts = line.trim().split("\\s+".toRegex())
    if (parts.size >= 5) {
        val range = parts[0].split("-")
        val start = range[0].toLong(16)
        val end   = range[1].toLong(16)
        val perms = parts[1]
        // Scan readable regions that aren't system libs
        if (perms.contains('r') && !isSystemLib(parts.getOrNull(5))) {
            scanRegionForDex(start, end)
        }
    }
}
```

### Root Shell Wrapper
```kotlin
// For operations needing root (copying app data, etc.)
fun runAsRoot(cmd: String): String {
    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
    return process.inputStream.bufferedReader().readText()
}
```

---

## Dependencies (build.gradle)

```groovy
dependencies {
    implementation 'com.github.topjohnwu.libsu:core:5.2.2'   // root shell
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'com.google.code.gson:gson:2.10.1'          // JSON report
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
}
```

---

## Important Constraints

1. **No Frida** — the whole point is to avoid it
2. **Must work on rooted device** — root is available and required
3. **arm64 only** is fine (target device is arm64)
4. **Android 10 (API 29)** is the minimum target environment
5. **dpt-shell specific** but should work on unprotected APKs too
6. Keep DEX deduplication — same bytes = same file, don't save twice (use SHA-256)
7. Handle the case where the packer writes decrypted DEX to a temp file in `/data/data/<pkg>/` — check there too after triggering the load

---

## Deliverables

1. **Complete working Android project** (all source files)
2. **build.gradle** files (app + root level)
3. **AndroidManifest.xml** with correct permissions:
   - `READ_EXTERNAL_STORAGE`
   - `WRITE_EXTERNAL_STORAGE`
   - `MANAGE_EXTERNAL_STORAGE` (API 30+)
   - `QUERY_ALL_PACKAGES`
4. **README.md** explaining:
   - How to build
   - How to use
   - How each strategy works
   - Known limitations

---

## Success Criteria

The app successfully dumps the real DEX from `com.hul.shikhar.rssm` (dpt-shell packed) and saves readable `.dex` files that can be opened with jadx or dex2jar.

Output should look like:
```
[✓] Strategy A: 0 DEX found (classloader reflection)
[✓] Strategy B: 2 DEX found via memory scan
    → /sdcard/dex_dump/com.hul.shikhar.rssm/1717600000/dump_0_2847392.dex
    → /sdcard/dex_dump/com.hul.shikhar.rssm/1717600000/dump_1_184320.dex
[✓] Report saved: /sdcard/dex_dump/com.hul.shikhar.rssm/1717600000/report.json
```
