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
    // generateDocs rewrites docs/generated: never run the checks concurrently with it.
    mustRunAfter("generateDocs")
    // A hand edit of a generated doc must rerun GeneratedDocsTest.
    inputs
        .files(fileTree(rootDir.resolve("docs/generated")))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("generatedDocs")
}

// Rewrites docs/generated/ from the code: the same golden test, in update mode.
tasks.register<Test>("generateDocs") {
    group = "documentation"
    description = "Regenerates docs/generated/ from the code (GeneratedDocsTest in update mode)."
    val testSourceSet = sourceSets.test.get()
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    filter { includeTestsMatching("*GeneratedDocsTest") }
    systemProperty("pianoanchor.updateGeneratedDocs", "true")
    outputs.dir(rootDir.resolve("docs/generated"))
    outputs.upToDateWhen { false }
}
