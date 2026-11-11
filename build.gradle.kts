// Root build: aggregate verification tasks. Module configuration lives in build-logic convention plugins.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlinGradle {
        target("*.gradle.kts", "build-logic/**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
    kotlin {
        target("build-logic/**/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
}

tasks.register("fastCheck") {
    group = "verification"
    description = "Fast gate for every change: formatting, detekt, JVM unit tests and coverage in all modules."
    dependsOn("spotlessCheck")
    // Container projects (e.g. :core) have no build file and no tasks.
    dependsOn(subprojects.filter { it.buildFile.exists() }.map { "${it.path}:fastCheck" })
}
