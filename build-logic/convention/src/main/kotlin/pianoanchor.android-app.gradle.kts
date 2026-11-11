// Android application shell: composition root only (Activity, dependency wiring). No business logic here.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("pianoanchor.quality")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

android {
    namespace = "pianoanchor.app"
    compileSdk =
        catalog
            .findVersion("android-compileSdk")
            .get()
            .requiredVersion
            .toInt()
    defaultConfig {
        applicationId = "io.github.kaushikvapiwala.pianoanchor"
        minSdk =
            catalog
                .findVersion("android-minSdk")
                .get()
                .requiredVersion
                .toInt()
        targetSdk =
            catalog
                .findVersion("android-targetSdk")
                .get()
                .requiredVersion
                .toInt()
        versionCode = 1
        versionName = "0.0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

tasks.named("fastCheck") { dependsOn("testDebugUnitTest") }
