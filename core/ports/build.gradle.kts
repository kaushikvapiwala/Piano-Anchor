plugins {
    id("pianoanchor.kmp-library")
}

kotlin {
    explicitApi()
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
        }
    }
}
