// Plugins (kotlin-dsl and the Kotlin Gradle plugin it pulls in) resolve from Maven Central before the Plugin Portal.
// Without this block only the portal is used, and it redirects Maven Central artifacts to repo.maven.apache.org,
// bypassing the cloud sessions' Maven Central mirror (scripts/cloud_setup.sh), which then fails on HTTP 429.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "build-logic"
include(":convention")
