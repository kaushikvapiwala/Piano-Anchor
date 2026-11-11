// Pure Kotlin Multiplatform library: Android + JVM desktop targets.
// Domain modules (core/*, engine) must stay free of Android/Compose/DI imports.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("pianoanchor.quality")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    jvm("desktop") {
        // Compile against the Java 17 API so JDK 21+-only calls fail at build time, not at runtime.
        compilerOptions { freeCompilerArgs.add("-Xjdk-release=17") }
    }
    android {
        namespace = "pianoanchor." +
            project.path
                .removePrefix(":")
                .replace(':', '.')
                .replace("-", "")
        compileSdk =
            catalog
                .findVersion("android-compileSdk")
                .get()
                .requiredVersion
                .toInt()
        minSdk =
            catalog
                .findVersion("android-minSdk")
                .get()
                .requiredVersion
                .toInt()
    }
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Pure-Kotlin modules require 100% line and branch coverage (owner rule). Exceptions must be annotated
// with @ExcludeFromCoverage("reason") so they are visible in review.
kover {
    currentProject {
        sources { excludedSourceSets.add("androidMain") }
    }
    reports {
        filters { excludes { annotatedBy("*ExcludeFromCoverage") } }
        verify {
            rule("100% line coverage") {
                bound {
                    minValue.set(100)
                    coverageUnits.set(kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE)
                }
            }
            rule("100% branch coverage") {
                bound {
                    minValue.set(100)
                    coverageUnits.set(kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH)
                }
            }
        }
    }
}

tasks.named("desktopTest", Test::class) { useJUnitPlatform() }
// koverVerify runs the desktop tests it measures.
tasks.named("fastCheck") { dependsOn("koverVerify") }
