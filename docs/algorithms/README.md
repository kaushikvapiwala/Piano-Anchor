# Algorithm notes

Short notes on **why** the engine works the way it does: the intent and evidence behind each algorithm.
Numbers are not repeated here; they live in `engine/src/commonMain/kotlin/pianoanchor/engine/Tunables.kt`,
where every value carries `Why:` and `Source:` in its KDoc (enforced by `architecture-tests/`).

One file per engine area (matcher, analysis, learner model, scheduler, session composer) is added together
with the code it explains. Update the note in the same PR whenever the approach changes.

## Index

- `docs/algorithms/sight-reading.md`: the generated sight-reading exercises and the staircase that picks their level.
- `docs/algorithms/note-names.md`: which keys of a strip get a letter, and how the letters fade as key names are learned.
