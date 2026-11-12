plugins {
    id("pianoanchor.kmp-library")
}

kotlin {
    android { namespace = "pianoanchor.engine" }
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
