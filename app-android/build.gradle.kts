plugins {
    id("pianoanchor.android-app")
}

dependencies {
    implementation(project(":ui:shell"))
    implementation(libs.androidx.activity.compose)
}
