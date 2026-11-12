// Fakes, abstract port contract tests and fixture helpers shared by all test source sets.
plugins {
    id("pianoanchor.kmp-library")
}

kotlin {
    android { namespace = "pianoanchor.testing" }
    sourceSets {
        commonMain.dependencies {
            api(project(":core:ports"))
            implementation(project(":engine"))
            api(libs.kotlinx.coroutines.test)
            api(kotlin("test"))
        }
        // Contract tests live in main source sets, so pick the JUnit flavour of kotlin-test explicitly.
        named("desktopMain").dependencies { api(kotlin("test-junit5")) }
        androidMain.dependencies { api(kotlin("test-junit")) }
    }
}
