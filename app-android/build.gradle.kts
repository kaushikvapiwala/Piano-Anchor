plugins {
    id("pianoanchor.android-app")
}

dependencies {
    implementation(project(":ui:shell"))
    implementation(project(":feature:monitor"))
    implementation(project(":platform:android-midi"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
}
