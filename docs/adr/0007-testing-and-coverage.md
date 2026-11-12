# 0007. Testing strategy and 100% coverage on pure modules

- Status: accepted
- Date: 2026-11-11

## Context
AI-assisted development needs fast, mechanical proof that code works and follows the design.

## Decision
Kover requires 100% line and branch coverage on pure-Kotlin modules; UI is covered by screenshot and
Compose UI tests. Test pyramid: JVM unit tests, port contract tests against real adapters, UI tests, a few
emulator end-to-end tests; a real-piano checklist per release. Exceptions use `@ExcludeFromCoverage(reason)`.

## Consequences
Coverage shows code ran, not that assertions are good: mutation testing on the engine is planned.
