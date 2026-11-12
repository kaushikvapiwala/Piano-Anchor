import pianoanchor.catalog
import pianoanchor.int

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
        versionCode = 1
        versionName = "0.0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}
