// Applied to every module: formatting (ktlint via Spotless), static analysis (detekt), coverage (Kover).
plugins {
    id("com.diffplug.spotless")
    id("dev.detekt")
    id("org.jetbrains.kotlinx.kover")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(catalog.findVersion("ktlint").get().requiredVersion)
    }
    kotlinGradle {
        target("*.gradle.kts")
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

// Per-module fast gate; the root `fastCheck` aggregates these. Each module type adds its tests below.
tasks.register("fastCheck") {
    group = "verification"
    description = "Fast gate: formatting, detekt, JVM unit tests and coverage for this module."
    dependsOn("spotlessCheck", "detekt")
}
