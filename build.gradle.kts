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

val docsCheck =
    tasks.register<Exec>("docsCheck") {
        group = "verification"
        description = "Checks that paths, links and modules referenced in Markdown docs exist (scripts/check_docs.py)."
        workingDir(rootDir)
        commandLine("python3", "scripts/check_docs.py")
        // Not cached: the result depends on git state (which files exist), which Gradle can't fingerprint.
        outputs.upToDateWhen { false }
    }

tasks.register("fastCheck") {
    group = "verification"
    description = "Checks root/build-logic formatting and docs; subprojects contribute their own fastCheck."
    // Gradle also runs `fastCheck` in every subproject that has one (task-name selection).
    dependsOn("spotlessCheck", docsCheck)
}
