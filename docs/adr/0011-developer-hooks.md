# 0011. Developer hooks: native git hooks and Claude Code hooks

- Status: accepted
- Date: 2026-11-11

## Context
CI catches every problem, but late. Agents and humans should hit the same checks while working: formatting,
`fastCheck`, DCO sign-off, and guards for mistakes we have made (a nested repository staged by accident).
Hook managers (lefthook, pre-commit) are good open-source tools, but each is one more thing every contributor
must install, and our hooks only call Gradle tasks that already exist.

## Decision
- Git hooks are plain POSIX shell scripts in `.githooks/`, enabled once per clone with
  `./gradlew installGitHooks` (sets `core.hooksPath`). pre-commit: Spotless on staged Kotlin, then
  `fastCheck` (which includes the gitlink guard); commit-msg: DCO sign-off; pre-push: `ciBuild`, the same
  task CI's build job runs. Claude Code cloud sessions get the hooks from `scripts/cloud_setup.sh`; there
  pre-push skips `ciBuild` (minutes in a container, and CI runs it on every PR).
- Claude Code hooks live in `.claude/settings.json` with Python scripts in `.claude/hooks/`: deny edits
  outside the repo, in `.git/` or in generated docs (docs/generated, planned); ask the user before edits to
  hook or settings files; format each edited Kotlin file (Spotless IDE hook); block stopping while
  `fastCheck` fails (reported once per unchanged tree, so it can't loop).
- The hooks are tested by `scripts/test_hooks.py` (`./gradlew hooksTest`, part of `fastCheck`). CI re-runs
  every check, so hooks are a convenience, never the only gate.

## Consequences
No extra tools beyond git, a POSIX shell and Python 3 (already needed by `docsCheck`). Revisit lefthook if
hooks grow parallel jobs or per-file globs. `git commit --no-verify` skips the hooks; CI still enforces.
