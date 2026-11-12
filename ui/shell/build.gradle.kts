plugins {
    id("pianoanchor.kmp-compose")
}

kotlin {
    android { namespace = "pianoanchor.ui.shell" }
    sourceSets {
        commonMain.dependencies {
            api(libs.compose.resources)
        }
    }
}

compose.resources {
    packageOfResClass = "pianoanchor.ui.shell.resources"
    publicResClass = true
}
