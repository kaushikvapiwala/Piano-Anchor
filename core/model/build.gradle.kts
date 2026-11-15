plugins {
    id("pianoanchor.kmp-library")
}

kotlin {
    android { namespace = "pianoanchor.core.model" }
    explicitApi()
    sourceSets {
        commonMain.dependencies {
            // Review days are LocalDates (ADR 0022); the type is in the public API.
            api(libs.kotlinx.datetime)
        }
    }
}
