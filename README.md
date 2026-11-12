# Piano Anchor

An open-source, offline piano coach for Android that listens to your digital piano over MIDI, finds your
weak spots and plans each day's practice around them. Early development; nothing to install yet.

## Build

Requirements: JDK 25 (Gradle downloads it automatically if missing) and the Android SDK
(set `sdk.dir` in `local.properties` or `ANDROID_HOME`).

```sh
./gradlew fastCheck                    # formatting, detekt, unit tests, 100% coverage gate
./gradlew spotlessApply                # auto-format Kotlin (ktlint, official Kotlin style)
./gradlew :app-android:assembleDebug   # Android APK
./gradlew :app-desktop:run             # desktop development harness
```

Licensed under the [Apache License 2.0](LICENSE).
