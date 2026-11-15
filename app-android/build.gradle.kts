plugins {
    id("pianoanchor.android-app")
}

// Emulator end-to-end test (docs/adr/0021-emulator-end-to-end-tests.md): `./gradlew :app-android:atdPixelDebugAndroidTest`
// runs src/androidTest on a Gradle Managed Device (needs KVM), against the virtual piano in src/debug. Not part of
// fastCheck, check or ciBuild; e2e.yml runs it.
android {
    buildFeatures { buildConfig = true }
    defaultConfig {
        // The GitHub repository the update check asks for its latest release: the one place a fork changes.
        buildConfigField("String", "UPDATE_REPOSITORY", "\"kaushikvapiwala/Piano-Anchor\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // With the orchestrator, every test starts on an empty app: no database, no known pianos.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }
    // Release signing (docs/RELEASING.md): release.yml decodes the keystore to a temp file and sets these four
    // variables. Without them (local builds) the release APK is built unsigned, which still checks the R8 rules.
    val keystoreFile = System.getenv("ANDROID_KEYSTORE_FILE")
    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreFile != null) signingConfig = signingConfigs.getByName("release")
        }
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
    implementation(project(":feature:ear"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:practice"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:today"))
    implementation(project(":engine"))
    implementation(project(":platform:android-midi"))
    implementation(project(":platform:android-audio"))
    implementation(project(":platform:android-network"))
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
