pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Lets Gradle provision the pinned daemon/toolchain JDK (gradle/gradle-daemon-jvm.properties) when missing.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "piano-anchor"

include(
    ":core:model",
    ":core:ports",
    ":engine",
    ":testing",
    ":platform:android-midi",
    ":platform:android-audio",
    ":platform:android-network",
    ":platform:storage",
    ":ui:shell",
    ":feature:monitor",
    ":feature:ear",
    ":feature:onboarding",
    ":feature:practice",
    ":feature:lesson",
    ":feature:settings",
    ":feature:songs",
    ":feature:progress",
    ":feature:today",
    ":app-android",
    ":app-desktop",
    ":architecture-tests",
)
