# 0008. Executable specifications over prose specs

- Status: accepted
- Date: 2026-11-11

## Context
Spec-driven development tools add large markdown specs that drift; a 2026 study of 88k PRs found spec
artifacts did not reduce defects, while pass/fail checks are what keep agents correct.

## Decision
Behaviour is specified by tests, fixtures, schemas and architecture rules. Prose is limited to `AGENTS.md`,
`docs/ARCHITECTURE.md`, ADRs, the glossary and short intent-only notes in `docs/algorithms/`. Numbers live
once, in `Tunables.kt`. A docs check verifies that referenced paths and modules exist.

## Consequences
Less to keep in sync; correctness rides on checks. Large features may use a short design note in an issue.
