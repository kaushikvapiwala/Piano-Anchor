plugins {
    id("pianoanchor.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

dependencies {
    implementation(project(":ui:shell"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    // TEST ONLY: must fail Dependency Review (GPL). Never merge.
    implementation("com.mysql:mysql-connector-j:9.7.0")
}

compose.desktop {
    application {
        mainClass = "pianoanchor.desktop.MainKt"
    }
}
