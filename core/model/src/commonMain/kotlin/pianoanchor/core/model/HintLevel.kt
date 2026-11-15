package pianoanchor.core.model

/**
 * How much help a run of a passage starts with: [FULL] shows the amber key band on the waiting step at once,
 * [DELAYED] only after the learner's usual pause, [OFF] only on request (`docs/algorithms/guidance.md`).
 */
public enum class HintLevel { FULL, DELAYED, OFF }
