# Algorithm notes

Short notes on **why** the engine works the way it does: the intent and evidence behind each algorithm.
Numbers are not repeated here; they live in `engine/src/commonMain/kotlin/pianoanchor/engine/Tunables.kt`,
where every value carries `Why:` and `Source:` in its KDoc (enforced by `architecture-tests/`).

One file per engine area (matcher, analysis, learner model, scheduler, session composer) is added together
with the code it explains. Update the note in the same PR whenever the approach changes.
