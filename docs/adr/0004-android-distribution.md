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

## Amendment (M3, update check)
The owner decided in the M3 review that the in-app update check ships on by default, with a switch in Settings.
Once a learner day, when Today opens, the app makes one anonymous HTTPS GET to GitHub's public Releases API for this
repository's latest release (`UpdateCheckPort`, implemented in `:platform:android-network`). If it is newer than the
installed version, Today shows a banner linking to the release page; the app never downloads or installs anything.
What is sent: the request line and two headers (`Accept`, `User-Agent: PianoAnchor/<version>`), no cookies, no
identifiers, nothing about the learner; GitHub sees the phone's IP address as with any web request. With the switch
off no request is made at all. This is the app's only network use: `AGENTS.md` makes it the one exception to "no
network calls", and an architecture test allows networking imports only in `:platform:android-network`. Details:
`docs/protocols/update-check.md`.

