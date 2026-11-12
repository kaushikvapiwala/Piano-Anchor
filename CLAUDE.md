# CLAUDE.md

@AGENTS.md

## Claude Code specifics
- Hooks (`.claude/settings.json`) format edited Kotlin files, deny edits outside the repo, in `.git/` or
  generated docs (docs/generated, planned), and block finishing while `fastCheck` fails. Fix the cause; never bypass them.
- Run `./gradlew fastCheck` before reporting work as complete and paste the result.
- Pre-push self-review: `/code-review`, then `/simplify`.
- Prefer background tasks for long waits (CI, builds) and keep working on independent tasks meanwhile.
