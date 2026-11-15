package pianoanchor.core.model

/**
 * How a run is played: [PRACTICE] shows live verdicts and the faded key hints; [PERFORM] is one take with no hints and
 * no live verdicts, like playing from a music stand (`docs/algorithms/guidance.md`). Perform needs a timed rung.
 */
public enum class RunMode { PRACTICE, PERFORM }
