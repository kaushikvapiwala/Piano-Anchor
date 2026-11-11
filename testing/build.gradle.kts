// Fakes, abstract port contract tests and fixture helpers shared by all test source sets.
plugins {
    id("pianoanchor.kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:ports"))
            api(kotlin("test"))
        }
        // Contract tests live in main source sets, so pick the JUnit flavour of kotlin-test explicitly.
        named("desktopMain").dependencies { api(kotlin("test-junit5")) }
        androidMain.dependencies { api(kotlin("test-junit")) }
    }
}
