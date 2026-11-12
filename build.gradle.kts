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

val hooksTest =
    tasks.register<Exec>("hooksTest") {
        group = "verification"
        description = "Tests the git hooks, Claude Code hooks and settings, and docsCheck (scripts/test_hooks.py)."
        workingDir(rootDir)
        commandLine("python3", "-I", "scripts/test_hooks.py")
        // Up to date until a tested file or the tests change (the marker file is the output).
        inputs.files("scripts", ".githooks", ".claude/hooks", ".claude/settings.json")
        val marker = layout.buildDirectory.file("hooksTest/passed")
        outputs.file(marker)
        doLast { marker.get().asFile.writeText("ok") }
    }

val gitlinkCheck =
    tasks.register<Exec>("gitlinkCheck") {
        group = "verification"
        description = "Fails if a nested git repository (gitlink) is staged (scripts/check_no_gitlinks.sh)."
        workingDir(rootDir)
        commandLine("sh", "scripts/check_no_gitlinks.sh")
        // Not cached: the result depends on the git index.
        outputs.upToDateWhen { false }
    }

tasks.register<Exec>("installGitHooks") {
    group = "build setup"
    description = "Points git at the repo's hooks in .githooks/ (pre-commit, commit-msg, pre-push). Run once per clone."
    workingDir(rootDir)
    commandLine("git", "config", "core.hooksPath", ".githooks")
}

tasks.register("fastCheck") {
    group = "verification"
    description = "Checks root/build-logic formatting, docs and hooks; subprojects contribute their own fastCheck."
    // Gradle also runs `fastCheck` in every subproject that has one (task-name selection).
    dependsOn("spotlessCheck", docsCheck, gitlinkCheck, hooksTest)
}

// Modules with a build file (container projects like `:core` have none). Each applies pianoanchor.quality,
// so each has `fastCheck` and `check`; a module without them fails ciBuild loudly ("task not found").
val modulePaths = subprojects.filter { it.buildFile.exists() }.map { it.path }

// What CI's build job runs; the pre-push hook runs the same task so the two can't drift.
// Task names inside dependsOn() resolve in the root project only (name selection across modules happens
// only on the command line), so every module's tasks are listed by path.
tasks.register("ciBuild") {
    group = "verification"
    description = "Everything CI builds and verifies: fastCheck and check in every module, and both apps."
    dependsOn("fastCheck", "check", ":app-android:assembleDebug", ":app-desktop:compileKotlin")
    dependsOn(modulePaths.flatMap { listOf("$it:fastCheck", "$it:check") })
}
