import pianoanchor.DEV_VERSION_CODE
import pianoanchor.DEV_VERSION_NAME
import pianoanchor.catalog
import pianoanchor.int
import pianoanchor.releaseVersionCode

// Android application shell: composition root only (Activity, dependency wiring). No business logic here.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("pianoanchor.quality")
}

android {
    namespace = "pianoanchor.app"
    compileSdk = catalog.int("android-compileSdk")
    defaultConfig {
        applicationId = "io.github.kaushikvapiwala.pianoanchor"
        minSdk = catalog.int("android-minSdk")
        targetSdk = catalog.int("android-targetSdk")
        // A release build passes -PpianoanchorVersion=X.Y.Z (release.yml, from the tag); anything else is a dev build.
        val release = providers.gradleProperty("pianoanchorVersion").orNull
        versionName = release ?: DEV_VERSION_NAME
        versionCode = release?.let(::releaseVersionCode) ?: DEV_VERSION_CODE
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}
