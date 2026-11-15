# 0022. kotlinx-datetime for learner days

- Status: accepted
- Date: 2026-11-15

## Context
The learner model (M2) schedules reviews per learner day: a calendar date with no time of day. The review log
(`LearnerRepositoryPort`) keys reviews by skill and day, and due days are compared and shifted by whole days. The domain
modules (`core/model`, `core/ports`, `engine`) may not use `java.*`, so `java.time.LocalDate` is out, and the Kotlin
standard library has an `Instant` but no calendar date.

## Decision
- Use `kotlinx.datetime.LocalDate` (`org.jetbrains.kotlinx:kotlinx-datetime`, Apache-2.0, JetBrains, Kotlin
  Multiplatform) for `Review.day` and the days of `SkillState`. It is a kotlinx library, so AGENTS.md allows it in the
  domain; `core/model` exposes it with `api`.
- Only `LocalDate` is used (parsing, printing and day arithmetic). Instants stay `kotlin.time.Instant`.
- The SQL adapter stores days as ISO `yyyy-mm-dd` text, which sorts like dates do.

Rejected: an own epoch-day type (reinvents a maintained library, and its calendar maths would need its own tests) and
epoch days as plain `Int`s (loses type safety and readability).

## Consequences
One more dependency, pinned in `gradle/libs.versions.toml`. Which clock and time zone define "today" is decided by the
code that records reviews (M2.4), not by this library.
