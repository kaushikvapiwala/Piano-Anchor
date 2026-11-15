# 0023. Content in Kotlin, notes as text

- Status: accepted
- Date: 2026-11-15

## Context
M3 ships three units of passages. Each passage needs notes, metadata and a rights record proving it may ship, and the
rights document has to stay in step with the content. Writing steps by hand as `Step(onset, ScoreNote(...))` calls is
unreadable at that scale and easy to get wrong.

## Decision
- Units, skills and passage metadata stay typed Kotlin in `engine/src/commonMain/kotlin/pianoanchor/engine/content` (`Curriculum`). The compiler checks the
  shape, and tests check the invariants (unique ids, known skills, rights present, bars full).
- A passage's notes are one compact text line (`C4q D4q E4h`), parsed once by `NoteText`: a small ABC-like subset of our
  own (grammar in `docs/algorithms/note-text.md`).
- Every `Passage` carries a required typed `Rights` record. `docs/generated/rights.md` is generated from the
  curriculum by `generateDocs`, so a stale file fails the build.
- Reading content does no I/O, so there is no port yet. When MusicXML import lands (M6, ADR 0010) the content moves to
  files behind a content port, and `Curriculum` becomes its first adapter's data.

Rejected: JSON files behind a port now (a port, an adapter and a format for content that only ships inside the app),
and a full ABC or LilyPond parser (not Kotlin Multiplatform, too big, some GPL).

## Consequences
No new dependency. Passages are reviewed as text in diffs. The curriculum moves again at M6, but the note text and the
`Rights` record carry over.
