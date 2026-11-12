# Contributing to Piano Anchor

Thanks for helping! Piano Anchor is an open-source, offline piano coach. Contributions of code, piano
support, curriculum ideas and bug reports are all welcome.

## Getting started

1. Install the Android SDK (Android Studio is easiest). Gradle downloads the right JDK (25) by itself.
2. Clone the repo and run `./gradlew fastCheck`. It should pass before you change anything.
   Then run `./gradlew installGitHooks` once: commits are auto-formatted and checked, pushes run the CI build.
3. Run the apps: `./gradlew :app-android:assembleDebug` or `./gradlew :app-desktop:run`.

## How we work

- **One feature per pull request.** Small PRs get reviewed and merged quickly.
- **Branch from `main`**, e.g. `feat/wait-mode-matcher`, `fix/ble-reconnect`.
- **Conventional Commits** for the PR title (it becomes the squash commit on `main`):
  `feat(engine): …`, `fix(midi): …`, `docs: …`, `build: …`, `ci: …`, `chore: …`, `refactor(…): …`, `test(…): …`.
- **Sign off every commit** (Developer Certificate of Origin, below): `git commit -s`.
- **Everything is tested.** Pure-Kotlin modules require 100% line and branch coverage (`koverVerify`);
  UI is covered by screenshot and UI tests. `./gradlew fastCheck` must pass. CI runs it on every PR.
- **Formatting is automatic:** `./gradlew spotlessApply` (ktlint, official Kotlin style). Don't hand-format.
- **Architecture:** domain code (`core/*`, `engine`) has no Android, Compose or DI imports; platform code
  lives in adapters behind ports. Architecture rules are enforced by tests.
- PRs are **squash-merged**; `main` only changes through pull requests.
- **Automated checks** on every PR: CI build and verify (`fastCheck`, `check`, app builds), PR conventions
  (Conventional Commit title, DCO sign-off), Dependency Review (blocks new dependencies with moderate+
  vulnerabilities or GPL/AGPL licences), CodeQL code scanning, and actionlint for workflow files.

## Using AI assistants

AI-assisted contributions are welcome. This repo is set up for coding agents. You are responsible for
every line you submit:

- **Disclose** AI use in the PR checklist (which tool).
- **Understand and test** the change yourself; include the commands you ran and their results.
- For anything beyond a small fix, **open or link an issue first** so we agree on the approach.
- Low-effort generated PRs (untested, unexplained, or unrelated churn) will be closed.

## Clean-room rules (important)

- **No GPL code** (the project is Apache-2.0). Permissive code (MIT, BSD, Apache-2.0) only, with its notice.
- **No code, text or assets copied from piano-vendor apps or commercial learning apps**, even if you
  decompiled them. Facts about MIDI protocols written in your own words are fine.
- **No copyrighted music.** Bundled pieces must be public domain (with a rights record) or original.

## Developer Certificate of Origin

By signing off (`Signed-off-by: Your Name <email>`), you certify the
[Developer Certificate of Origin 1.1](https://developercertificate.org/): you wrote the change or have the
right to submit it under the project's licence. Use your real name or a consistent pseudonym, and a GitHub
private email if you prefer (`ID+username@users.noreply.github.com`). Fix a missing sign-off with
`git commit --amend -s` or `git rebase --signoff main`.

## Adding support for a piano

Every MIDI keyboard already works for notes, velocity and pedals. Remote-control support for specific
models will come as data profiles plus a read-only probe tool. Until then, open a "Support for my piano"
issue with your model and its MIDI Implementation link.

## Reporting security issues

See [SECURITY.md](SECURITY.md). Please don't open public issues for vulnerabilities.

By participating you agree to the [Code of Conduct](CODE_OF_CONDUCT.md).
