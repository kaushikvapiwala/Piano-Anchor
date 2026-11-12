import pianoanchor.catalog
import pianoanchor.int

// Shared Kotlin Multiplatform setup: Android + JVM desktop targets. Each module sets its own Android namespace.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("pianoanchor.quality")
}

kotlin {
    jvm("desktop") {
        // Compile against the Java 17 API so JDK 21+-only calls fail at build time, not at runtime.
        compilerOptions { freeCompilerArgs.add("-Xjdk-release=17") }
    }
    android {
        compileSdk = catalog.int("android-compileSdk")
        minSdk = catalog.int("android-minSdk")
    }
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.named("desktopTest", Test::class) { useJUnitPlatform() }
