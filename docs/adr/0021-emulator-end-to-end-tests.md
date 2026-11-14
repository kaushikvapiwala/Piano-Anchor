# 0021. Emulator end-to-end tests with a virtual MIDI piano

- Status: accepted
- Date: 2026-11-14

## Context
ADR 0007 puts a few end-to-end tests on an emulator at the top of the pyramid. Until now the Android glue (the
`MidiManager` code in `:platform:android-midi`, the Koin wiring in `:app-android`, SQLite on a device) was checked only
by the owner on a real piano. An emulator has no piano, and overriding Koin with a fake input would skip exactly the
glue the test is for.

## Decision
- **A virtual piano in the debug app:** `VirtualPianoService`, a framework `MidiDeviceService` declared only in
  `app-android/src/debug/` and disabled in its manifest; the test enables it before it starts and resets it after, so
  a debug install on a real device lists no test piano. (It first lived in `app-android/src/androidTest/`, but a
  service in the test APK runs in the test package's own process, and AGP leaves the Kotlin stdlib, which the tested
  app already has, out of the test APK: the service crashed with `NoClassDefFoundError` on the first Identity
  Request.) The app lists and opens it through `MidiManager` like a USB piano, so the real
  `AndroidMidiAdapter` runs. Input port 0 is the app's and answers the Identity Request with a test identity (the
  MIDI non-commercial manufacturer id `7D`, so it names no vendor); input port 1 is the test's control port and is
  forwarded unchanged to the output port. Each Android input port takes one sender, hence one port each. No MIDI
  library is needed.
- **The test drives only the UI** (`createAndroidComposeRule<MainActivity>`, nodes found by the Compose resource
  strings, which is why `:feature:monitor` and `:feature:practice` make their `Res` class public). Before playing it
  waits until the app has opened the piano's output port, which the test sees in the device status the MIDI service
  reports, and replays a run's first note if the app dropped it while connecting its receiver.
- **Gradle Managed Devices** (AGP, already present): one device, `atdPixel` (Pixel 6, the newest `aosp-atd`
  Automated Test Device image, API 36). Run with `./gradlew :app-android:atdPixelDebugAndroidTest` (needs KVM).
- **AndroidX Test Orchestrator** with `clearPackageData`: each test starts on an empty database.
- **New test-only dependencies** (Apache-2.0): `androidx.test:runner`, `androidx.test:rules`, `androidx.test.ext:junit`,
  `androidx.test:orchestrator`, `androidx.test.services:test-services` and `androidx.compose.ui:ui-test-junit4`.
- **CI:** `.github/workflows/e2e.yml` runs it on pushes to main, nightly, on demand and on the PRs that change the test
  (the workflow, `app-android/src/androidTest/`, `app-android/src/debug/`, `app-android/build.gradle.kts`). It is not part of `fastCheck`,
  `check` or `ciBuild`, and not a required check: an emulator run takes minutes and ordinary PRs don't touch the glue.

Rejected: Koin overrides with `FakePiano` (skip the Android MIDI glue) and a hand-written emulator script (loses
GMD's image caching and device setup).

## Consequences
The `MidiManager` glue, Koin wiring and SQLite are exercised on every main commit; the Identity Request round trip
and the monitor-to-practice hand-over included. Bluetooth LE scanning, app restarts and real pianos stay on the
owner's hardware checklist, and so does the USB transport mapping: the virtual piano lists as a virtual device,
not a USB one. ATD images strip parts of the system: if one ever lacks the MIDI service (feature
`android.software.midi`), the device switches to the plain `aosp` image at the same API level. Code the test APK declares for the system to start on its own (services, receivers) must not use Kotlin or
other libraries the app already has; such code goes in `app-android/src/debug/` instead. Bump `ui-test-junit4`
with `ui-test-manifest` (the AndroidX Compose version Compose Multiplatform resolves to).
