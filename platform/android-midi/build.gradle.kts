// Driven adapter: MIDI ports over Android's MIDI service (android.media.midi).
// The adapter logic sits in commonMain behind the internal MidiSystem seam, so it runs and is measured on the JVM
// (desktop target, tests only); androidMain holds only the thin MidiManager glue (excluded from coverage).
plugins {
    id("pianoanchor.kmp-library")
}

kotlin {
    android { namespace = "pianoanchor.platform.androidmidi" }
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
