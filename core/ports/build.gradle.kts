plugins {
    id("pianoanchor.kmp-library")
}

kotlin {
    android { namespace = "pianoanchor.core.ports" }
    explicitApi()
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(libs.kotlinx.coroutines.core)
        }
    }
}
