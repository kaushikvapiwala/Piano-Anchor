# 0006. Build on JDK 25 with detekt 2.0 (pre-release)

- Status: accepted
- Date: 2026-11-11

## Context
The project uses Kotlin 2.4. The last stable detekt (1.23.8) is built for Kotlin 2.0 and JDK 21 and cannot
run on JDK 25.

## Decision
Pin the Gradle daemon to JDK 25 (`gradle/gradle-daemon-jvm.properties`, auto-provisioned) and use detekt
2.0.0-alpha.6, which targets Kotlin 2.4 and JDK 25. Compile to Java 17 bytecode with `-Xjdk-release=17`.

## Consequences
An alpha tool is a build gate; the version is pinned and upgraded deliberately. Move to detekt 2.0 final
when released.
