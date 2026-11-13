// Driving adapter: the wait-mode practice screen and its ViewModel. Depends on ports and the engine only.
// Screenshots: Roborazzi on Robolectric in androidHostTest; `verifyRoborazziAndroidHostTest` runs in fastCheck,
// `./gradlew :feature:practice:recordRoborazziAndroidHostTest` rewrites the goldens (docs/adr/0018-screenshot-tests.md).
plugins {
    id("pianoanchor.kmp-compose")
    alias(libs.plugins.roborazzi)
}

kotlin {
    android {
        namespace = "pianoanchor.feature.practice"
        withHostTest { isIncludeAndroidResources = true }
    }
    explicitApi()
    sourceSets {
        commonMain.dependencies {
            api(project(":core:ports"))
            implementation(project(":engine"))
            implementation(project(":ui:shell"))
            implementation(libs.compose.resources)
            api(libs.androidx.lifecycle.viewmodel)
        }
        commonTest.dependencies {
            implementation(project(":testing"))
        }
        named("androidHostTest").dependencies {
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.robolectric)
            implementation(libs.junit4)
        }
    }
}

compose.resources {
    packageOfResClass = "pianoanchor.feature.practice.resources"
}

tasks.named("fastCheck") { dependsOn("verifyRoborazziAndroidHostTest") }

// Robolectric downloads its Android framework jar at test time, outside Gradle (no caching, no repository
// settings). Resolve it through Gradle instead and run Robolectric offline against that copy.
val robolectricSdk by configurations.creating
dependencies { robolectricSdk(libs.robolectric.android.all) }
// Kept under the Gradle user home, not build/: Robolectric's native loader reads the jar location back from a URL
// without decoding it, so a space in the checkout path (e.g. "Digital Piano App") made the jar "missing".
val robolectricSdkDir =
    layout.dir(
        provider {
            gradle.gradleUserHomeDir.resolve("piano-anchor/robolectric-sdk/${project.name}")
        },
    )
val syncRobolectricSdk =
    tasks.register<Sync>("syncRobolectricSdk") {
        from(robolectricSdk)
        into(robolectricSdkDir)
    }
tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    dependsOn(syncRobolectricSdk)
    inputs.files(syncRobolectricSdk).withPropertyName("robolectricSdk").withPathSensitivity(PathSensitivity.NAME_ONLY)
    // Robolectric reaches into JDK internals (FileDescriptor) and loads its native graphics library.
    jvmArgs(
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED",
    )
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricSdkDir.get().asFile.absolutePath)
}
