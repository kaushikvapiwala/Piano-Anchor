---
name: next-task
description: Do the next task in Piano Anchor's queue (docs/ROADMAP.md → "Next up"), one small PR per session. Opus orchestrates and reviews, the Sonnet `implementer` subagent writes code, Haiku scouts look things up. Arguments - none (owner present), `unattended`, `grill`, `dry-run`.
---

# Next task (Piano Anchor)

You are the **Opus orchestrator** for one task. The aim is low usage and high quality: one small task per
session, with a small context. `docs/ROADMAP.md`, `AGENTS.md`, the GitHub issues and the code carry everything
between sessions, so nothing has to live in this conversation. The skill works the same locally and in Claude
Code cloud sessions.

- **Queue:** `docs/ROADMAP.md` → section "Next up" (ordered; the first unfinished item is the task).
- **Specs:** GitHub issues labelled `spec` (+ `ready` once approved). Never committed.
- **Owner questions:** label `needs-owner` on the issue, questions in an issue comment.
- **Research notes are private and outside the repo.** Never open, quote or commit them. A slice that needs a
  fact not yet in a repo doc (protocol or algorithm notes under `docs/`) or its issue needs the owner.

## Modes (the skill argument)
| Argument | Who is there | Behaviour |
|---|---|---|
| *(none)* | owner at the keyboard | normal: may ask the owner, grill, show drafts |
| `unattended` | nobody (`.claude/skills/next-task/scripts/run-loop.sh` or a cloud routine) | **never ask or wait.** When a step needs the owner, record it (below) and stop with `blocked-owner` |
| `grill` | owner, batch decisions | grill the owner on the next 2–4 **decision-heavy** slices that have no spec issue yet; post each agreed spec as an issue labelled `spec` + `ready`; no code. Status `done` |
| `dry-run` | anyone | orient only: change nothing; report which task you would pick, whether it needs the owner, and the status you would end with |

**Recording an owner question (unattended):** comment on the task's issue (create a plain issue if there is
none) with numbered questions, each with your recommended answer, and add the label `needs-owner`. Mark the
"Next up" item `(needs-owner: #N)` only if you are already editing `docs/ROADMAP.md` in a PR; otherwise the
label is the record. Then stop.

**Last line of every run** (the runner parses only your final message, so if a Stop hook makes you continue,
repeat the line at the end; nothing after it):
`NEXT_TASK_STATUS: <done|blocked-owner|blocked-ci|queue-empty|error> — <one-line summary>`
- `blocked-ci`: the same PR's CI failed again after one real fix attempt in an earlier session (say so in a PR comment with the attempt count).
- `queue-empty`: "Next up" has no item this mode can do.

## GitHub access
Use `gh` when `gh auth status` succeeds. In cloud sessions `gh` has no valid token: use the GitHub MCP tools
instead (e.g. `create_pull_request`, `enable_pr_auto_merge`, issue and PR read tools). Same steps, other tool.

## Which model does what
Pick the cheapest model that can do a step **without lowering quality**, and keep this session's context small.
| Step | Model | Why |
|---|---|---|
| Orchestration, spec writing, `/code-review`, `/simplify`, answering implementer questions | **Opus** (this session) | Judgement; mistakes here cost the most |
| Writing code and tests from a spec | **Sonnet** (`implementer` subagent) | Most tool calls happen here; a tight spec makes Sonnet enough |
| Read-only scouting: find files or symbols and **quote them verbatim** with paths; summarise a failing CI log down to the failing step and its error lines | **Haiku** (`Agent` with `model: "haiku"`, `subagent_type: "Explore"` for searches) | Mechanical lookups; verbatim quotes keep it lossless |

Haiku locates and quotes, it never decides. Facts that go into a spec must come back **verbatim with their
source**, and Opus checks them first. Don't spawn a subagent for something one `grep` or a short file read can
answer: each subagent has a cold-start cost. Always use the aliases `opus`, `sonnet`, `haiku`, never pinned IDs.

## 1. Orient (keep it cheap)
0. Check the session runs **inside the repo** (repo root has `AGENTS.md`). Started elsewhere, the repo's
   `.claude/settings.json` hooks (format, path guard, Stop `fastCheck`) and the `implementer` agent are not
   active: tell the owner to restart inside the repo; until then run `./gradlew fastCheck` yourself before
   every commit and use the fallback implementer (step 2).
1. Read `docs/ROADMAP.md` (short). Read issues only as the task needs them.
2. `git status -sb`, `git log --oneline -5`, open PRs and the last few merged ones. **Reconcile first:** an
   open PR from an earlier session is this session's task (fix CI with new commits, re-queue auto-merge). A
   merged PR whose "Next up" item is still listed gets removed in this session's PR. Trust git and GitHub over
   the roadmap. Switch to an up-to-date `main` before branching.
3. The task is the **first unfinished item in "Next up"** whose issue is not labelled `needs-owner`. Say in one
   line which task you picked. If it needs an owner decision: attended, ask it first; unattended, record it and
   stop with `blocked-owner`. Never skip ahead past a blocked item that later items depend on (brackets in
   "Next up" list dependencies).

## 2. Do the task, by type
| Slice kind | Examples | Before the spec |
|---|---|---|
| **Decision-heavy** (product, UX, algorithm, protocol facts) | device adapters, the monitor, matcher, instrument identity, practice screen | needs an owner-approved spec: a `spec` + `ready` issue from a `grill` session. Attended: grill now. **Unattended without one: `blocked-owner`** ("needs a grill session for 1.x") |
| **Mechanical** (well-defined code, chores) | parser, ports, storage adapter, CLI tools, docs, CI | no grilling. Decide yourself and add an **"Assumptions"** section to the issue listing each choice and why; the owner can override by commenting |

**Grilling (attended or `grill` mode only):** invoke the `grilling` skill if available, otherwise ask rounds of
numbered questions, each with a recommended answer worded so "yes" accepts. Cover API shape, error model, edge
cases, tunable values, test cases and scope. Facts are your job (roadmap, ADRs, `AGENTS.md`, code, a Haiku
scout); only real decisions go to the owner. If nothing is open, say so in one line and write the spec. Write
every agreed decision into the spec, so the implementer never has to ask. Before any UI question, read `docs/ui/`
and the owner's design mock (the "Piano Coach Mockup" artifact; list artifacts to find it): screens follow the mock,
so ask only where they differ from it or from `docs/ui/`.

**Build vs. buy before every spec.** For each non-trivial piece, check: existing code → a dependency already
in `gradle/libs.versions.toml` → a well-known library (Haiku scout gathers 2–3 candidates with licence, KMP
support, last release, size, verbatim). Use a library if it is maintained, licence-compatible
(Apache-2.0/MIT/BSD/EPL; never GPL/AGPL/LGPL), KMP-capable for shared modules, and replaces real code; the PR
adds an ADR. Build only with a stated reason. `core/*` and `engine` stay free of non-kotlinx libraries.

**Spec template (every field required)**
- Goal (1–2 lines) and out of scope
- Modules/files to create or touch
- **Hexagon placement** (ADR 0003, `docs/ARCHITECTURE.md`): each new type's layer: domain (`core/model`,
  `engine`), port (`core/ports` `*Port` + `<Port>ContractTest` in `testing/` + `Fake*`), driven adapter
  (`*Adapter` running the contract test; libraries live here), driving adapter (`feature/*`, tools), or
  composition root (`app-*`, Koin wiring only). Dependencies point inward only
- Public API: exact Kotlin signatures (types, nullability, suspend/Flow, error model)
- Behaviour rules + named `Tunables` entries (value, unit, why, source)
- Tests: named cases with input → expected output; contract tests for any port/adapter
- **Build vs. buy:** pieces, candidates considered, choice and why
- Repo docs to add or update (algorithm and protocol notes under `docs/`, ADR for a new dependency)
- **Data-flow check:** for every output (file, UI, request), an existing port or type carries the data it needs
- Done when: `fastCheck` green, listed tests pass, docs updated, PR conventions met

Issues are public: facts go in in your own words. Decision-heavy specs: the owner approves the draft before
posting. Mechanical specs: check public-safety, post with Assumptions.

**Code, tests, config, workflows: delegate to Sonnet by default.**
- **Brief:** for a spec issue, "Implement issue #N". For a chore: goal, files, constraints, done-when.
- **Who:** the `implementer` subagent. If "Agent type 'implementer' not found", spawn `Agent` with
  `subagent_type: "general-purpose"`, `model: "sonnet"`, and the brief plus the body of
  `.claude/agents/implementer.md`. Never with `isolation: "worktree"` (hooks resolve the main checkout).
- **Do it yourself only** when the change is tiny (about 20 lines), or mainly diagnosis or orchestration text.
- Afterwards check the branch and run `./gradlew fastCheck` yourself: never trust a reported green.
- A question or deviation from the implementer: answer it from the roadmap, ADRs or code (owner only if
  needed), **edit the issue** so the spec stays the source of truth, re-run if needed, and add a line to the
  spec template above or `AGENTS.md` so the next spec covers it (ratchet rule).

## 3. Review on Opus (always)
Run `/code-review` on the branch diff against `main` and fix real findings. Cleanup by what the PR touches:
| PR touches | Cleanup |
|---|---|
| Only docs, CI, config, hooks, scripts | none |
| Kotlin source | one inline pass yourself: reuse, simplification, efficiency, altitude. No `/simplify` |
| The last PR of a milestone | full `/simplify` on the whole milestone diff, as its own small cleanup PR |

Also check the hexagon beyond what Konsist catches (adapter or library types in port signatures, domain logic
in adapters or ViewModels, adapters without their contract test) and reinvention (code duplicating the stdlib,
an existing dependency or an obvious library). Fixes go in **new commits**; never amend or force-push. Run
`fastCheck` again after fixes.

## 4. Ship
- Update `docs/ROADMAP.md` in the branch: remove the finished "Next up" item, add follow-ups you found, flip
  phase statuses. It ships with the PR, so `main` is right the moment it merges.
- Push, open a ready (not draft) PR with a Conventional Commit title and `Closes #N`, queue auto-merge
  (squash). **Don't wait for CI or the merge;** the next session checks with one call. If CI failed, a Haiku
  scout pulls the failing step and error lines from the failed run's log; fix with new commits.
- Commits: `git commit -s`, Conventional Commit message, the co-author trailer for the current model.

## 5. Clean cut, then stop
- Ratchet rule: a mistake an agent could repeat gets a check, a test, or one line in `AGENTS.md` (in this PR
  or as a "Next up" item).
- Leave the working tree clean (the runner stops on stray changes).
- Report in a few lines: task, PR link, follow-ups. End with the `NEXT_TASK_STATUS:` line and **stop**. Don't
  start the next task: a fresh session is cheaper and avoids context drift.

## Never
- Open or commit private research notes; write SysEx to unverified addresses; add GPL, vendor or competitor code.
- Use pinned model IDs.
- Do more than one task per session.
