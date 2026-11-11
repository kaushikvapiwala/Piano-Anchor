plugins {
    id("pianoanchor.kmp-library")
}

kotlin {
    explicitApi()
    sourceSets {
        commonMain.dependencies {
            api(project(":core:ports"))
        }
        commonTest.dependencies {
            implementation(project(":testing"))
        }
    }
}
