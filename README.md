# Piano Anchor

An open-source, offline piano coach for Android that listens to your digital piano over MIDI, finds your
weak spots and plans each day's practice around them. Early development; nothing to install yet.

## Privacy

Your practice data stays on your device. The app has no analytics and no accounts. Its one network call is an
update check, on by default and switched off in Settings: once a day it asks GitHub's public Releases API for this
repository's latest release and, if there is a newer one, shows a link on the Today screen. The request carries no
identifier and nothing about you (`docs/protocols/update-check.md`).

## Build

Requirements: JDK 25 (Gradle downloads it automatically if missing) and the Android SDK
(set `sdk.dir` in `local.properties` or `ANDROID_HOME`).

```sh
./gradlew fastCheck                    # formatting, detekt, unit tests, 100% coverage gate
./gradlew spotlessApply                # auto-format Kotlin (ktlint, official Kotlin style)
./gradlew :app-android:assembleDebug   # Android APK
./gradlew :app-desktop:run             # desktop development harness
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). AI-assisted contributions are welcome under the rules there.

Licensed under the [Apache License 2.0](LICENSE).
