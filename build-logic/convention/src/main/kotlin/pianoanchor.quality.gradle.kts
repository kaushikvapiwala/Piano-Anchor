import pianoanchor.catalog

// Applied to every module: formatting (ktlint via Spotless), static analysis (detekt), coverage (Kover).
plugins {
    id("com.diffplug.spotless")
    id("dev.detekt")
    id("org.jetbrains.kotlinx.kover")
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(catalog.findVersion("ktlint").get().requiredVersion)
    }
    kotlinGradle {
        // A file, not the "*.gradle.kts" glob: Spotless would walk the whole module directory, build/ included, while
        // parallel compiles rewrite it.
        target(files("build.gradle.kts"))
        ktlint(catalog.findVersion("ktlint").get().requiredVersion)
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom(fileTree("src") { include("**/*.kt") })
}

// One JVM bytecode target for every module (Android-compatible).
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

// A failed test prints its name, message and test-code stack frames even under --quiet: the Stop hook and Claude
// Code sessions (GRADLE_OPTS in .claude/settings.json) run Gradle quietly, which otherwise shows only the report path.
tasks.withType<AbstractTestTask>().configureEach {
    testLogging.quiet {
        events(org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED)
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Per-module fast gate. `./gradlew fastCheck` runs it in every module; koverVerify runs the JVM tests it measures.
tasks.register("fastCheck") {
    group = "verification"
    description = "Fast gate: formatting, detekt, JVM unit tests and coverage for this module."
    dependsOn("spotlessCheck", "detekt", "koverVerify")
}
