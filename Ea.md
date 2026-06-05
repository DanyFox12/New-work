# Task: Build an Android DEX Extraction Tool

## Project Overview

Build a standalone Android application that extracts DEX bytecode files
from packed APKs using ART runtime internals and reflection.
This is an academic tool for APK analysis and reverse engineering research
on apps you own or have permission to analyze.

---

## Technical Background

Some APKs use runtime loading techniques (e.g. dpt-shell) where:
- The installed `classes.dex` is a small loader stub
- The real bytecode is loaded into memory at runtime by a native library
- Standard static analysis tools only see the stub, not the real code

This tool extracts the real DEX from memory after the runtime loads it.

**Known target characteristics:**
- Loader stub classes: `ProxyApplication`, `JniBridge`, `ProxyComponentFactory`
- Native loader: `libdpt.so`
- Encoded assets: stored in `assets/vwwwwwvwww/`
- Target package: `com.hul.shikhar.rssm`
- Device: arm64, Android 10 (API 29), rooted

---

## Stack

- **Language**: Kotlin + C++ (JNI where needed)
- **Min SDK**: 26
- **Target SDK**: 34
- **Build**: Gradle with AGP 8.x
- **Root access**: via `libsu`

---

## Core Features

### 1. DEX Extraction Engine

Three independent extraction strategies — run all, collect results:

**Strategy A — ClassLoader Reflection**
```
Load target APK via DexClassLoader in our process
→ Reflect into BaseDexClassLoader.pathList.dexElements
→ For each element: get dexFile → read bytes
→ Save valid DEX files
```

**Strategy B — Memory Region Scan**
```
After loading the APK:
→ Parse /proc/self/maps
→ Identify readable non-system memory regions
→ Search for DEX magic bytes: 64 65 78 0a ("dex\n")
→ Parse DEX header at offset 32 for declared file size
→ Extract and save the region
```

**Strategy C — Filesystem Search**
```
With root access:
→ Copy APK to working directory, unzip
→ Inspect assets/ for encoded DEX containers
→ Check /data/data/<pkg>/app_dex/ and /data/data/<pkg>/files/
→ Trigger package load, then re-scan above paths
```

---

### 2. Application Compatibility Layer

When loading a third-party APK inside our process, some calls need
to be intercepted to prevent our host app from terminating:

- `Debug.isDebuggerConnected()` → return `false`
- `System.exit(int)` → no-op
- `Runtime.halt(int)` → no-op
- `ActivityManager.getRunningAppProcesses()` → return filtered list

Optional native layer (`native_compat.cpp`):
- Intercept `exit()` / `abort()` via `Interceptor.replace` pattern
  to keep our host process alive if the loaded code calls them

---

### 3. DEX Validation

Before saving any extracted bytes:
```kotlin
val DEX_MAGIC = byteArrayOf(0x64, 0x65, 0x78, 0x0a)

fun isValidDex(bytes: ByteArray): Boolean {
    if (bytes.size < 112) return false
    // Check magic
    if (!bytes.take(4).toByteArray().contentEquals(DEX_MAGIC)) return false
    // Check version (035–039)
    val version = String(bytes.slice(4..6).toByteArray())
    if (version < "035" || version > "040") return false
    // Check declared size matches
    val declaredSize = ByteBuffer.wrap(bytes, 32, 4)
        .order(ByteOrder.LITTLE_ENDIAN).int
    return declaredSize == bytes.size
}
```

Deduplication via SHA-256 — never save the same bytes twice.

---

### 4. Output

Save extracted DEX files to:
```
/sdcard/dex_output/<package_name>/<unix_timestamp>/
    dump_0_<size>.dex
    dump_1_<size>.dex
    report.json
```

`report.json` structure:
```json
{
  "package": "com.example.app",
  "timestamp": 1717600000,
  "loader_type": "dpt-shell",
  "dex_files": [
    {
      "index": 0,
      "path": "/sdcard/dex_output/.../dump_0_2847392.dex",
      "size_bytes": 2847392,
      "sha256": "abc123..."
    }
  ]
}
```

---

### 5. UI

Single Activity, minimal design:
- `EditText` — package name input
- `Button` — "Extract DEX"
- `RecyclerView` or `ScrollView` — real-time log output
- `TextView` — final status (success / partial / failed)

---

## Project Structure

```
dex-extractor/
├── app/
│   ├── src/main/
│   │   ├── kotlin/com/tools/dexextractor/
│   │   │   ├── MainActivity.kt
│   │   │   ├── ExtractionEngine.kt      ← orchestrates all strategies
│   │   │   ├── StrategyA.kt             ← ClassLoader reflection
│   │   │   ├── StrategyB.kt             ← memory region scan
│   │   │   ├── StrategyC.kt             ← filesystem search
│   │   │   ├── CompatLayer.kt           ← intercept exit/debug calls
│   │   │   ├── DexVerifier.kt           ← magic + header validation
│   │   │   ├── ReportWriter.kt          ← JSON output
│   │   │   └── RootShell.kt             ← su command wrapper
│   │   ├── cpp/
│   │   │   └── native_compat.cpp        ← native exit/abort interception
│   │   ├── res/layout/
│   │   │   └── activity_main.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle
└── build.gradle
```

---

## Key Code Snippets

### ClassLoader Reflection (Strategy A)
```kotlin
fun extractViaReflection(apkPath: String): List<ByteArray> {
    val loader = DexClassLoader(
        apkPath,
        cacheDir.absolutePath,
        null,
        classLoader
    )
    val baseDex = Class.forName("dalvik.system.BaseDexClassLoader")
    val pathListField = baseDex.getDeclaredField("pathList")
        .also { it.isAccessible = true }
    val pathList = pathListField.get(loader)

    val dplClass = Class.forName("dalvik.system.DexPathList")
    val elements = dplClass.getDeclaredField("dexElements")
        .also { it.isAccessible = true }
        .get(pathList) as Array<*>

    return elements.mapNotNull { element ->
        element ?: return@mapNotNull null
        val dexFileField = element.javaClass
            .getDeclaredField("dexFile")
            .also { it.isAccessible = true }
        val dexFile = dexFileField.get(element) ?: return@mapNotNull null
        readDexBytes(dexFile)
    }
}
```

### Memory Scan (Strategy B)
```kotlin
fun scanMemoryForDex(): List<ByteArray> {
    val results = mutableListOf<ByteArray>()
    File("/proc/self/maps").readLines().forEach { line ->
        val parts = line.trim().split("\\s+".toRegex())
        if (parts.size < 2) return@forEach
        val perms = parts[1]
        if (!perms.contains('r')) return@forEach
        val pathname = parts.getOrNull(5) ?: ""
        if (isSystemPath(pathname)) return@forEach

        val (startHex, endHex) = parts[0].split("-")
        val start = startHex.toLongOrNull(16) ?: return@forEach
        val end   = endHex.toLongOrNull(16)   ?: return@forEach
        val size  = (end - start).toInt()
        if (size < 10 * 1024) return@forEach

        extractDexFromRegion(start, size)?.let { results.add(it) }
    }
    return results
}
```

### Root Shell
```kotlin
object RootShell {
    fun exec(cmd: String): String = Shell.cmd(cmd).exec().out.joinToString("\n")
    fun copy(src: String, dst: String) = exec("cp -f '$src' '$dst'")
    fun listDir(path: String): List<String> =
        exec("ls '$path' 2>/dev/null").lines().filter { it.isNotBlank() }
}
```

---

## Dependencies

```groovy
dependencies {
    implementation 'com.github.topjohnwu.libsu:core:5.2.2'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'com.google.code.gson:gson:2.10.1'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0'
}
```

---

## AndroidManifest Permissions

```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"/>
```

---

## Constraints

1. No Frida dependency
2. Requires rooted device (root is available)
3. arm64 architecture target
4. Android 10 (API 29) minimum
5. Must handle dpt-shell packed APKs as primary use case
6. SHA-256 deduplication — never write identical DEX twice
7. Check `/data/data/<pkg>/` subdirectories after triggering load

---

## Deliverables

1. Complete Kotlin Android project (all source files ready to build)
2. `app/build.gradle` and root `build.gradle`
3. `AndroidManifest.xml` with all required permissions
4. `README.md` with build instructions and usage guide

After writing all files, run:
```bash
./gradlew assembleDebug
```
Fix all compilation errors until the build succeeds cleanly.

---

## Expected Output

```
[✓] Strategy A: 0 files (ClassLoader reflection)
[✓] Strategy B: 2 files found via memory scan
    → /sdcard/dex_output/com.hul.shikhar.rssm/1717600000/dump_0_2847392.dex
    → /sdcard/dex_output/com.hul.shikhar.rssm/1717600000/dump_1_184320.dex
[✓] Report: /sdcard/dex_output/com.hul.shikhar.rssm/1717600000/report.json
```

---

Build this complete Android project exactly as specified.
Start with the project structure and gradle files, then implement
each Kotlin file. After all files are written, run
`./gradlew assembleDebug` and fix any compilation errors until
the build succeeds cleanly.
