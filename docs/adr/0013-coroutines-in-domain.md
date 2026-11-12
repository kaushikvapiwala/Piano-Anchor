# 0013. kotlinx.coroutines in the domain for Flow-based ports

- Status: accepted
- Date: 2026-11-11

## Context
The MIDI ports must deliver a stream of device lists and a stream of timestamped messages. The note monitor,
the matcher and the ViewModels all consume streams, and the Android and desktop adapters produce them from
callbacks.

## Decision
- `:core:ports` depends on `kotlinx-coroutines-core` (`api`) so ports can expose `Flow`. It is the only new
  import the domain allows. It is KMP-native, Apache-2.0, and already in the version catalog (desktop uses it).
- `:testing` depends on `kotlinx-coroutines-test` (`api`) for `runTest` in contract tests, and on `:engine`
  (`implementation`, not exposed to its users) so
  `FakePiano` decodes bytes with the real `MidiParser`.
- Alternative rejected: callback listener interfaces. Every consumer would then rebuild a `Flow` on top of them.

## Consequences
Ports are asynchronous by design; adapters bridge platform callbacks with `callbackFlow`/`Channel`.
The architecture rules still forbid Android, Compose, Koin and `java.*` in the domain.
