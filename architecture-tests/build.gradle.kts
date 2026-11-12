// Konsist rules that enforce the hexagonal architecture and project conventions (see docs/ARCHITECTURE.md).
plugins {
    id("pianoanchor.jvm")
}

dependencies {
    testImplementation(libs.konsist)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Konsist reads every source file in the repo: declare them as inputs so the task reruns only when code changes.
tasks.test {
    inputs
        .files(
            fileTree(rootDir) {
                include("**/src/**/*.kt")
                exclude("**/build/**", "build-logic/**")
            },
        ).withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("repositorySources")
}
