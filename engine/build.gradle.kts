plugins {
    id("pianoanchor.kmp-library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android { namespace = "pianoanchor.engine" }
    explicitApi()
    sourceSets {
        commonMain.dependencies {
            api(project(":core:ports"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(project(":testing"))
        }
    }
}
