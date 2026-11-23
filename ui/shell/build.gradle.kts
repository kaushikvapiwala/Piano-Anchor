// Shared UI: theme, navigation rail, back button. Composable tests run on Robolectric in androidHostTest.
plugins {
    id("pianoanchor.kmp-compose")
}

kotlin {
    android {
        namespace = "pianoanchor.ui.shell"
        withHostTest { isIncludeAndroidResources = true }
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.compose.resources)
        }
        named("androidHostTest").dependencies {
            implementation(libs.robolectric)
            implementation(libs.junit4)
            implementation(libs.compose.ui.test)
            implementation(libs.androidx.compose.ui.test.manifest)
        }
    }
}

compose.resources {
    packageOfResClass = "pianoanchor.ui.shell.resources"
    publicResClass = true
}

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
