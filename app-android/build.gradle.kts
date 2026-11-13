plugins {
    id("pianoanchor.android-app")
}

dependencies {
    implementation(project(":ui:shell"))
    implementation(project(":feature:monitor"))
    implementation(project(":platform:android-midi"))
    // In-memory instrument store until the SQLDelight adapter lands (roadmap slice 1.8); remove then.
    implementation(project(":testing"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
}
