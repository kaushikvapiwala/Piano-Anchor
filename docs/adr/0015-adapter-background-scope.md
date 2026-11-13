# 0015. Adapters that work in the background take an injected CoroutineScope

- Status: accepted
- Date: 2026-11-13

## Context
Slice 1.4b makes `AndroidMidiAdapter` reopen a dropped Bluetooth LE MIDI device with a backoff of seconds to
minutes, after `connect` has returned. That work needs a coroutine that outlives the call, and its timing has to be
testable without real waiting. Ports stay free of lifecycle methods (ADR 0013 allows `Flow` and `suspend` only).

## Decision
- An adapter that works in the background takes a `CoroutineScope` as a parameter of its internal constructor and
  launches that work there. It never creates a hidden global scope.
- The platform factory (e.g. `AndroidMidiAdapter(context)`) passes an app-lived scope
  (`SupervisorJob() + Dispatchers.Default`): the adapter is app-scoped, as the MIDI device callback already is.
- Tests pass `backgroundScope` from `runTest`, so backoff delays run in virtual time; port contract tests that need
  background work receive the scope through their harness factory (`createHarness(scope)`).
- Alternative rejected: a `close()` or `start()` on the port. It adds lifecycle to the domain-facing API for a
  concern that only the composition root has.

## Consequences
Background jobs stop only when their scope is cancelled or the adapter's own rules end them (e.g. `disconnect`,
giving up). Wiring code that creates an adapter decides its lifetime by the scope it passes.
