# 0002. Kotlin Multiplatform with Compose Multiplatform

- Status: accepted
- Date: 2026-11-10

## Context
The app must be easy to maintain, smooth, and able to reach iOS later. Real-time MIDI timing matters more
than native widgets; the main screens are custom-drawn (score, cursor, heatmaps).

## Decision
One Kotlin codebase: shared engine and shared Compose UI. Targets now: Android and JVM desktop (the desktop
app is a development harness with live USB MIDI). iOS is deferred; shared code stays platform-neutral.
Platform specifics (MIDI, Bluetooth, audio, sync) sit behind ports with per-platform adapters.

## Consequences
One language and one UI to maintain. Native SwiftUI or Flutter were rejected (two UIs; plugin-based MIDI).
Adding iOS later means an iOS target plus a CoreMIDI adapter.
