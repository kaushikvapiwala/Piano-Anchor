# 0012. Reference docs generated from code and checked by a golden test

- Status: accepted
- Date: 2026-11-11

## Context
Reference tables (every tunable with its rationale, every port with its contract) go stale when written by
hand. Agents also need that context cheaply: a short generated file beats reading every source file.

## Decision
- `docs/generated/tunables.md` and `docs/generated/ports.md` are rendered from source (KDoc and
  declarations) with Konsist, which the architecture tests already use.
- `GeneratedDocsTest` in `architecture-tests/` renders each doc and fails when the committed file differs, so
  a stale file or a hand edit fails `fastCheck` and CI. A second test fails on files in `docs/generated/`
  that no generator produces.
- `./gradlew generateDocs` runs the same test in update mode (system property
  `pianoanchor.updateGeneratedDocs=true`) and rewrites the files. It is never up to date, and the normal `test`
  task runs after it.

## Consequences
After changing `Tunables` or a port (or its KDoc), run `./gradlew generateDocs` and commit the result. The
files are never edited by hand; the Claude Code hook denies edits under `docs/generated/`. Further
generated docs (driver matrix, schema reference) are added as one more render function and file name.
