# 0024. Logcat logging in the platform glue

- Status: accepted
- Date: 2026-11-15

## Context
The first on-device checks (a phone and a real piano) need a trace of what the app saw: which MIDI devices came and
went, which ports opened, what a Bluetooth LE scan found, why a connect or reconnect failed, which database or network
call failed. Until now the app logged one line (the metronome writer's failure), so logcat showed crashes and the
system's own MIDI and Bluetooth lines but nothing of the app. The screens catch `StorageUnavailableException` and only
show it, so storage failures left no trace at all.

## Decision
- The `androidMain` glue of the platform adapters logs with `android.util.Log`, one tag per module, all starting
  `PianoAnchor` (`PianoAnchorMidi`, `PianoAnchorNet`, `PianoAnchorDb`, `PianoAnchorAudio`). The glue sees every
  platform event (device list changes, opens and closes, scans, Bluetooth opens, HTTP status, SQL exceptions), and
  each reconnect attempt shows as one more Bluetooth open.
- Levels: `i` for lifecycle events, `d` for detail (scan results, Bluetooth LE unavailable), `w` for failures.
- The release build's R8 rules (`app-android/proguard-rules.pro`) remove `Log.v`, `Log.d` and `Log.i`, so a release
  logs warnings only. Detail lines with Bluetooth addresses and device serials stay in debug builds.
- The domain and the adapters' `commonMain` logic stay free of logging. No logging port and no library.
- Never log MIDI note bytes (one line per key press floods logcat; the monitor's fixture export records them).

Rejected: a logging port in `core/ports` (a contract test, fakes and wiring in every view model, for lines that only a
developer at a cable reads) and a logging library such as Kermit or Napier (a dependency for a few calls).

## Consequences
How to capture logs on a phone is in `docs/debugging.md`. Decisions made in `commonMain` (when the reconnect policy
gives up, a missed Identity Reply) are not logged; if a device check needs them, add a logging seam to that adapter
then. Logs stay on the device: logcat is read only over `adb`, and nothing is sent anywhere.
