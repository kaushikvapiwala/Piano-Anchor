// Driven adapter: the instrument and attempt stores in an on-device SQLite database (SQLDelight, ADR 0019).
// The adapters sit in commonMain over SQLDelight's SqlDriver and are tested on the JVM against the JDBC SQLite driver
// (desktop target, tests only); androidMain holds only the Android driver factory (excluded from coverage).
plugins {
    id("pianoanchor.kmp-library")
    alias(libs.plugins.sqldelight)
}

kotlin {
    android { namespace = "pianoanchor.platform.storage" }
    explicitApi()
    sourceSets {
        commonMain.dependencies {
            api(project(":core:ports"))
            // SqlDriver is in the adapters' public constructors.
            api(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        named("desktopMain").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        commonTest.dependencies {
            implementation(project(":testing"))
        }
    }
}

sqldelight {
    databases {
        create("PianoAnchorDatabase") {
            packageName.set("pianoanchor.platform.storage.db")
            dialect(libs.sqldelight.dialect.sqlite330)
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}

// SQLDelight's generated code (queries, schema) is the library's, not ours: its own tests cover it.
kover { reports { filters { excludes { packages("pianoanchor.platform.storage.db") } } } }
