# 0019. SQLDelight for on-device storage

- Status: accepted
- Date: 2026-11-13

## Context
Slice 1.8 keeps instruments and graded attempts across app restarts, behind `InstrumentRepositoryPort` and
`AttemptRepositoryPort`. Data stays on the device (ADR 0009). The store needs typed queries, transactions (an attempt
and its events are written together), a `Flow` that re-emits after writes (`AttemptRepositoryPort.recent`) and
schema migrations that are checked before they reach a learner's phone.

## Decision
- Use **SQLDelight 2.x** (Apache-2.0, Kotlin Multiplatform: Android and JVM) in a new driven-adapter module,
  `:platform:storage`. The schema and queries are SQL files (`src/commonMain/sqldelight`); SQLDelight generates typed
  Kotlin for them. The coroutines extension turns a query into a `Flow`.
- The SQL dialect is SQLite 3.30, the newest SQLDelight dialect that Android minSdk 31's SQLite supports.
- The adapters (`SqlInstrumentRepositoryAdapter`, `SqlAttemptRepositoryAdapter`) take a `SqlDriver` and a
  `CoroutineDispatcher` for the blocking queries. A driver factory per platform, `createPianoAnchorDatabaseDriver`,
  creates or migrates the schema and turns foreign keys on: Android's opens `piano-anchor.db` in the app's database
  directory; the JVM one opens a given file and also serves the tests (in-memory SQLite).
- Migrations are verified by the SQLDelight plugin (`verifyMigrations`): each schema version is committed as
  `src/commonMain/sqldelight/databases/<version>.db`, and every `.sqm` migration must turn the previous version into
  the current `CREATE` statements. The verify task runs in `check`.
- SQLDelight types never leave `:platform:storage`, except `SqlDriver` in the adapters' constructors and the
  factories, which only the composition root sees. The generated package `pianoanchor.platform.storage.db` is left out
  of Kover; the adapters stay at 100% on the JVM driver, and the Android factory is `@ExcludeFromCoverage`.
- Alternatives rejected: **Room KMP** (Apache-2.0) works but is annotation- and KSP-driven, and its schema checks
  are weaker for SQL-first work; **JSON files** need hand-written transactions, indexing and migrations.

## Consequences
A schema change needs a new `.sqm` migration and a new committed `<version>.db` (`generateCommonMainPianoAnchorDatabaseSchema`).
Instants are stored as epoch milliseconds, so anything finer is dropped. The desktop harness is not wired to the
database yet; it gets a file under `~/.piano-anchor/` when a desktop feature first needs a repository.
