# 0018. Roborazzi screenshot tests on Robolectric

- Status: accepted
- Date: 2026-11-13

## Context
UI modules have no line-coverage gate (ADR 0007); screens are verified by screenshots. They must run on the JVM
inside `fastCheck`, in the cloud and in CI, without an emulator.

## Decision
- **Roborazzi** (Apache-2.0) with **Robolectric** native graphics, in each feature module's `androidHostTest`
  source set (Android KMP library plugin, `withHostTest { isIncludeAndroidResources = true }`). Chosen over
  Paparazzi for its Compose/Robolectric fit.
- Goldens are committed under the module's `screenshots/`, one per state, light and dark.
  `verifyRoborazziAndroidHostTest` is part of the module's `fastCheck`; `recordRoborazziAndroidHostTest` rewrites
  the goldens and is run by hand after an intended change (review the images in the PR).
- Robolectric's Android framework jar (`android-all-instrumented`) is resolved through Gradle (catalog entry
  `robolectric-android-all`) and Robolectric runs offline against it: Robolectric's own downloader bypasses
  Gradle's repositories and cache. Its version must match the Robolectric version and the test's SDK level.
- The host tests run on the JDK 25 toolchain with `--add-opens`/`--add-exports` for the JDK internals Robolectric
  uses.

## Consequences
Screens avoid infinite animations (they keep the test from going idle). Rendering differs slightly between
operating systems, so goldens are recorded on Linux (CI and cloud sessions).
