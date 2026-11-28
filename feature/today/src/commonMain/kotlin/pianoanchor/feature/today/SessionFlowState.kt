package pianoanchor.feature.today

import pianoanchor.core.model.BarRange
import pianoanchor.core.model.Skill
import pianoanchor.engine.session.SessionStep

/**
 * What the session flow shows: the [steps] of the run, the index [current] of the one running (or just finished,
 * while [between] shows), the card [between] two steps, and the [recap] once the last step is done. [pedalHint] is
 * true while a pedal tap would go to the next step.
 */
public data class SessionFlowState(
    val steps: List<SessionStep>,
    val current: Int,
    val between: Between? = null,
    val recap: SessionRecap? = null,
    val pedalHint: Boolean = false,
)

/** The card between two steps: the [next] step, the [secondsLeft] of its countdown and whether it is [paused]. */
public data class Between(
    val next: SessionStep,
    val secondsLeft: Int,
    val paused: Boolean,
)

/**
 * The weakest spot of today's practice: the [bars] of the passage titled [passageTitle]. The screen builds the
 * sentence from its strings.
 */
public data class RecapTip(
    val passageTitle: String,
    val bars: BarRange,
)

/**
 * The recap after the last step: [minutes] played, [stepsDone], the skills [learned] today (at most three) and a [tip],
 * or null when nothing stood out.
 */
public data class SessionRecap(
    val minutes: Int,
    val stepsDone: Int,
    val learned: List<Skill>,
    val tip: RecapTip?,
)
