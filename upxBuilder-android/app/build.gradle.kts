plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.upx.builder"
    compileSdk = 34
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.upx.builder"
        minSdk = 24
        // targetSdk 28 is deliberate and load-bearing: Android only permits an
        // app to EXECUTE binaries from its own files dir (the $PREFIX toolchain,
        // `pkg install …`) when targeting SDK <= 28. Termux and AndroidIDE pin
        // the same level for the same reason. Raising this breaks the terminal's
        // ability to run installed tools. (Fine for sideloading; Play Store
        // would require 34+ and disallow this technique.)
        targetSdk = 28
        versionCode = 8
        versionName = "1.7.0"
    }

    lint {
        // We accept the old targetSdk on purpose (see above).
        disable += "ExpiredTargetSdkVersion"
        disable += "OldTargetApi"
    }

    // The native C++ code analyzer (libupxanalyzer.so) that powers the
    // Problems panel — built with the NDK via CMake.
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Signing config is read from environment variables (or -P gradle properties)
    // so the keystore and passwords NEVER live in the repository.
    //   UPX_KEYSTORE           absolute path to your .jks keystore
    //   UPX_KEYSTORE_PASSWORD  keystore password
    //   UPX_KEY_ALIAS          key alias
    //   UPX_KEY_PASSWORD       key password
    fun secret(name: String): String? =
        System.getenv(name) ?: (project.findProperty(name) as String?)

    val hasReleaseKeystore = secret("UPX_KEYSTORE") != null

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = file(secret("UPX_KEYSTORE")!!)
                storePassword = secret("UPX_KEYSTORE_PASSWORD")
                keyAlias = secret("UPX_KEY_ALIAS")
                keyPassword = secret("UPX_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8: shrink + obfuscate code and strip unused resources so the app
            // is hard to reverse-engineer.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Use the release keystore when provided (UPX_KEYSTORE env vars);
            // otherwise fall back to the auto-generated debug key so the APK is
            // still installable. Either way the build is a full R8 release.
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        // Compatible with Kotlin 1.9.24
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM 2024.06.00 (Compose 1.6.8 / Material3 1.2.1) pairs cleanly with
    // Kotlin 1.9.24 and the 1.5.14 Compose compiler extension above.
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
