# 0016. kotlinx-serialization for the fixture format

- Status: accepted
- Date: 2026-11-13

## Context
The note monitor exports recorded sessions as JSONL fixtures that the matcher and replay tests read
(`fixtures/SCHEMA.md`). The codec belongs in `:engine` so every module and tool reads fixtures the same way, and
the domain allows only Kotlin and kotlinx libraries.

## Decision
- `:engine` uses `kotlinx-serialization-json` (Apache-2.0, KMP, JetBrains) with the Kotlin serialization compiler
  plugin, for private line DTOs inside `FixtureCodec`. The public types (`Fixture`) stay plain Kotlin.
- The architecture rules allow `kotlinx.serialization` imports in domain code, next to `kotlinx.coroutines`.
- Rejected: a hand-written JSON reader and writer (escaping and error handling we would have to test ourselves).

## Consequences
Later persisted formats (session logs, settings export) can reuse the same library. Kover counts the generated
serializers, so DTO fields carry no default values (their generated branches would need tests of their own).
