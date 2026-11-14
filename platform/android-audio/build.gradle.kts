// Driven adapter: the metronome over Android's AudioTrack (docs/protocols/android-audio.md).
// The scheduling logic sits in commonMain behind the internal AudioSink seam, so it runs and is measured on the JVM
// (desktop target, tests only); androidMain holds only the thin AudioTrack glue (excluded from coverage).
plugins {
    id("pianoanchor.kmp-library")
}

kotlin {
    android { namespace = "pianoanchor.platform.androidaudio" }
    explicitApi()
    sourceSets {
        commonMain.dependencies {
            api(project(":core:ports"))
            implementation(project(":engine"))
        }
        commonTest.dependencies {
            implementation(project(":testing"))
        }
    }
}
