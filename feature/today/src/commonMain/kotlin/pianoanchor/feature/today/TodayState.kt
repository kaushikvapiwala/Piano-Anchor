package pianoanchor.feature.today

import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.Release
import pianoanchor.core.model.RunMode
import pianoanchor.core.model.Skill
import pianoanchor.engine.lesson.Quiz
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.session.Card
import pianoanchor.engine.session.Session
import pianoanchor.engine.session.SessionStep
import pianoanchor.engine.session.WeekStatus

/** How many days back (today included) the practice-day line counts: the M3 exit check looks at 16 days. */
public const val PRACTICE_WINDOW_DAYS: Int = 16

/** The time of day the greeting names. */
public enum class Greeting { MORNING, AFTERNOON, EVENING }

/** How a piano is connected, read from its device id (`usb:` or `ble:`); null for any other id. */
public enum class Transport { USB, BLE }

/** The connected piano: [device], its [transport] and its calibrated latency offset in whole [latencyMs] (or null). */
public data class PianoChip(
    val device: MidiDevice,
    val transport: Transport?,
    val latencyMs: Int?,
)

/**
 * What Today shows. [session] is null until the plan has loaded (or when [storageFailed]); [steps] are its steps still
 * to do today, in run order (the finished ones left out). [doneToday] is true once the session's recap was reached.
 * [piano] is null while no piano is connected. [week] is the week's goal and pause status (null until loaded);
 * [practicedDays] counts the days with practice over the last [PRACTICE_WINDOW_DAYS] days. [update] is a newer release
 * to announce, or null.
 * [connecting] is true while the app reconnects the remembered piano in the background: the chip then says so
 * until the piano is listed or the attempt fails. [pedalControl] is the setting that lets a pedal tap start the
 * session.
 */
public data class TodayState(
    val session: Session? = null,
    val steps: List<SessionStep> = emptyList(),
    val doneToday: Boolean = false,
    val greeting: Greeting = Greeting.MORNING,
    val piano: PianoChip? = null,
    val week: WeekStatus? = null,
    val practicedDays: Int = 0,
    val storageFailed: Boolean = false,
    val update: Release? = null,
    val connecting: Boolean = false,
    val pedalControl: Boolean = false,
) {
    /** True when the Start button shows "Tap the pedal to continue": a piano is connected and a step is due. */
    val pedalHint: Boolean get() = pedalControl && piano != null && steps.isNotEmpty() && !doneToday

    /** The cold-open card, if something is due. */
    val coldOpen: Card.ColdOpen? get() = session?.cards?.filterIsInstance<Card.ColdOpen>()?.firstOrNull()

    /** True when the plan has loaded and offers neither a review nor a lesson: at most a song or sight-reading. */
    val caughtUp: Boolean get() = session != null && session.cards.none { it !is Card.Song && it !is Card.SightReading }

    /** The index of [step]'s card in [Session.cards]: what the trim view reports as skipped. */
    public fun cardIndex(step: SessionStep): Int = session?.cards?.indexOf(step.card) ?: -1
}

/** Where a tapped card leads. */
public sealed interface TodayTarget {
    /**
     * Practice [passageId] on [device] at [rung], in [mode] (a cold open performs). With [backing] it is the session's
     * play-along: chords and click.
     */
    public data class Practice(
        val device: MidiDevice,
        val passageId: String,
        val rung: TempoRung,
        val mode: RunMode = RunMode.PRACTICE,
        val backing: Boolean = false,
    ) : TodayTarget

    /** The five-step lesson of the course concept [skillId] on [device]. */
    public data class Lesson(
        val device: MidiDevice,
        val skillId: String,
    ) : TodayTarget

    /** The ear screen on [device], running [skills] in order. */
    public data class Ear(
        val device: MidiDevice,
        val skills: List<Skill>,
    ) : TodayTarget

    /** The review quizzes on [device], run in order. */
    public data class Quizzes(
        val device: MidiDevice,
        val quizzes: List<Quiz>,
    ) : TodayTarget

    /** The sight-reading exercise [passageId] on [device]: wait mode, from the staff alone. */
    public data class SightReading(
        val device: MidiDevice,
        val passageId: String,
    ) : TodayTarget

    /** No piano is connected: the Pianos screen. */
    public data object Pianos : TodayTarget
}

/** Where [card] leads on [device]: its lesson, exercise, practice, ear or quiz screen. */
internal fun targetOf(
    card: Card,
    device: MidiDevice,
    backing: Boolean = false,
): TodayTarget =
    when (card) {
        is Card.Lesson -> TodayTarget.Lesson(device, card.skill.id)
        is Card.SightReading -> TodayTarget.SightReading(device, card.passage.id)
        is Card.PassageCard -> TodayTarget.Practice(device, card.passage.id, card.rung, card.mode, backing)
        is Card.Ear -> TodayTarget.Ear(device, card.skills)
        is Card.Quizzes -> TodayTarget.Quizzes(device, card.quizzes)
    }
