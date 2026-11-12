import pianoanchor.catalog

// Kotlin Multiplatform library with Compose Multiplatform UI.
// UI is verified by screenshot and Compose UI tests, so it has no line-coverage gate.
plugins {
    id("pianoanchor.kmp-base")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val composeLibraries =
    listOf("compose-runtime", "compose-foundation", "compose-ui", "compose-material3")
        .map { catalog.findLibrary(it).get() }

kotlin {
    android {
        // Without this, composeResources (strings, images) are not packaged into the APK.
        androidResources { enable = true }
    }
    sourceSets {
        commonMain.dependencies {
            // api: public composables expose Compose types to consumers.
            composeLibraries.forEach { api(it) }
        }
    }
}
