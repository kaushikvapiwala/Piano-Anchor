## Summary
<!-- What changes and why. One feature per PR. Link the issue for non-trivial work: "Closes #123". -->

## Verification
<!-- Paste the command(s) you ran and the result, e.g. `./gradlew fastCheck` → BUILD SUCCESSFUL.
     For UI changes add a screenshot; for MIDI/piano changes say which instrument and connection you tested. -->

## Checklist
- [ ] PR title follows Conventional Commits (`feat(engine): …`, `fix(midi): …`), since it becomes the squash commit
- [ ] Every commit is signed off (`git commit -s`, see CONTRIBUTING.md)
- [ ] Tests added or updated; `./gradlew fastCheck` passes (100% coverage on non-UI modules)
- [ ] Docs updated where behaviour or setup changed
- [ ] No code or content copied from GPL projects, piano-vendor apps or competitor apps
- [ ] AI assistance used? If yes: which tool, and I have read, understood and tested every line
