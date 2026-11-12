# 0003. Hexagonal architecture

- Status: accepted
- Date: 2026-11-10

## Context
The engine must run identically on Android, desktop and in tests with recorded MIDI, and support many piano
models. Hardware and storage must be swappable.

## Decision
Ports and adapters (see `docs/ARCHITECTURE.md`): a pure-Kotlin domain with `*Port` interfaces, `*Adapter`
implementations outside it, contract tests per port. Enforced by Konsist rules in `architecture-tests/`.
Use cases only for logic shared by several callers, not one per screen.

## Consequences
Most tests run on the JVM without devices. Some ceremony (ports, contract tests) for each external concern.
