plugins {
    id("pianoanchor.android-app")
}

// Emulator end-to-end test (docs/adr/0021-emulator-end-to-end-tests.md): `./gradlew :app-android:atdPixelDebugAndroidTest`
// runs src/androidTest on a Gradle Managed Device (needs KVM). Not part of fastCheck, check or ciBuild; e2e.yml runs it.
android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // With the orchestrator, every test starts on an empty app: no database, no known pianos.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }
    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        managedDevices {
            localDevices {
                create("atdPixel") {
                    device = "Pixel 6"
                    // The newest Automated Test Device image; minSdk is 31.
                    apiLevel = 36
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }
}

dependencies {
    implementation(project(":ui:shell"))
    implementation(project(":feature:monitor"))
    implementation(project(":feature:practice"))
    implementation(project(":engine"))
    implementation(project(":platform:android-midi"))
    implementation(project(":platform:storage"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.junit4)
    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestUtil(libs.androidx.test.services)
}
