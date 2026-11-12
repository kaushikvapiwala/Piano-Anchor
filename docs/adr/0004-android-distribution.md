# 0004. Android 12+, phones and tablets, distributed as a GitHub APK

- Status: accepted
- Date: 2026-11-11

## Context
The app is a personal open-source project with no app-store plans.

## Decision
minSdk 31 (Android 12, ~78% of devices in Oct 2026; removes the location-gated Bluetooth permission path),
target/compile SDK 37. Layouts for phones and tablets in both orientations. Releases are signed APKs on
GitHub Releases with an opt-out in-app update check.

## Consequences
No store review or Play policies; users sideload. iOS would need the paid Apple programme (deferred).
