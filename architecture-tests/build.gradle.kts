// Konsist rules that enforce the hexagonal architecture and project conventions (see docs/ARCHITECTURE.md).
plugins {
    id("pianoanchor.jvm")
}

dependencies {
    testImplementation(libs.konsist)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Konsist reads every source file in the repo, which Gradle can't track as task inputs:
// always run, never reuse a cached result.
tasks.test {
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}
