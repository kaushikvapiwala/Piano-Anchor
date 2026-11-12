# CLAUDE.md

@AGENTS.md

## Claude Code specifics
- Hooks (`.claude/settings.json`) format edited Kotlin files, deny edits outside the repo, in `.git/` or
  generated docs (docs/generated), ask the user before hook/settings edits, and block finishing while
  `fastCheck` fails (main session and the `implementer` subagent). Fix the cause; never bypass them.
- Start each session with `/next-task` (`.claude/skills/next-task/`): one task from `docs/ROADMAP.md`
  "Next up" per session; `.claude/skills/next-task/scripts/run-loop.sh [N]` runs N unattended sessions.
- Run `./gradlew fastCheck` before reporting work as complete and paste the result.
- Pre-push self-review: `/code-review`, then the cleanup pass that `.claude/skills/next-task/SKILL.md` step 3
  sets for what the PR touches (`/simplify` on the whole diff at a milestone's end).
- Prefer background tasks for long waits (CI, builds) and keep working on independent tasks meanwhile.
