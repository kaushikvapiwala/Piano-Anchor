plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.gradle.plugin.kotlin)
    implementation(libs.gradle.plugin.compose.compiler)
    implementation(libs.gradle.plugin.android)
    implementation(libs.gradle.plugin.compose)
    implementation(libs.gradle.plugin.detekt)
    implementation(libs.gradle.plugin.spotless)
    implementation(libs.gradle.plugin.kover)
}
