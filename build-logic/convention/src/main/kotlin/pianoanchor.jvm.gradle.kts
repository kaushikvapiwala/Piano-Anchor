// Plain JVM module (desktop app, architecture tests, tools).
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("pianoanchor.quality")
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
        // Compile against the Java 17 API so JDK 21+-only calls fail at build time, not at runtime.
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
tasks.withType<Test>().configureEach { useJUnitPlatform() }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
