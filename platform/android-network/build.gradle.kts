// Driven adapter: the update check over one HTTPS GET to GitHub's Releases API (docs/protocols/update-check.md). The
// only module allowed to use networking APIs (architecture test). The request, headers and JSON handling sit in
// commonMain behind the internal HttpGetter seam, so they run and are measured on the JVM (desktop target, tests only);
// androidMain holds only the thin HttpsURLConnection glue (excluded from coverage).
plugins {
    id("pianoanchor.kmp-library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android { namespace = "pianoanchor.platform.androidnetwork" }
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
