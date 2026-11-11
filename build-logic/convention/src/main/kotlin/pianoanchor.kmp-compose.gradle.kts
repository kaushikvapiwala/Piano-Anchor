// Kotlin Multiplatform library with Compose Multiplatform UI.
plugins {
    id("pianoanchor.kmp-library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    android {
        // Without this, composeResources (strings, images) are not packaged into the APK.
        androidResources { enable = true }
    }
    sourceSets {
        commonMain.dependencies {
            // api: public composables expose Compose types to consumers.
            api(catalog.findLibrary("compose-runtime").get())
            api(catalog.findLibrary("compose-foundation").get())
            api(catalog.findLibrary("compose-ui").get())
            api(catalog.findLibrary("compose-material3").get())
        }
    }
}

// UI is verified by screenshot and Compose UI tests; coverage is reported here but not gated at 100%.
kover {
    reports {
        verify { warningInsteadOfFailure.set(true) }
    }
}
