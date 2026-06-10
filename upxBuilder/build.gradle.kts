import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "1.9.20"
    id("org.jetbrains.compose") version "1.5.11"
}

group = "com.upx"
version = "1.0.0"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")
}

kotlin {
    jvmToolchain(21)
}

compose.desktop {
    application {
        mainClass = "com.upx.builder.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "upxBuilder"
            packageVersion = "1.0.0"
            description = "upxBuilder — a beautiful multi-language IDE"
            vendor = "upxBuilder"
        }
    }
}
