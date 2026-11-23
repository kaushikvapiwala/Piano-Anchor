package pianoanchor.feature.today

import pianoanchor.core.model.DEFAULT_WEEKLY_GOAL_DAYS
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.Release
import pianoanchor.core.model.RunMode
import pianoanchor.core.model.Skill
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.session.Card
import pianoanchor.engine.session.Session

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
 * What Today shows. [session] is null until the plan has loaded (or when [storageFailed]); [piano] is null while no
 * piano is connected. [daysThisWeek] counts the days with a saved attempt since Monday against [weeklyGoalDays];
 * [practicedDays] counts them over the last [PRACTICE_WINDOW_DAYS] days. [update] is a newer release to announce, or
 * null.
 * [connecting] is true while the app reconnects the remembered piano in the background: the chip then says so
 * until the piano is listed or the attempt fails.
 */
public data class TodayState(
    val session: Session? = null,
    val greeting: Greeting = Greeting.MORNING,
    val piano: PianoChip? = null,
    val daysThisWeek: Int = 0,
    val weeklyGoalDays: Int = DEFAULT_WEEKLY_GOAL_DAYS,
    val practicedDays: Int = 0,
    val storageFailed: Boolean = false,
    val update: Release? = null,
    val connecting: Boolean = false,
) {
    /** The cold-open card, if something is due. */
    val coldOpen: Card.ColdOpen? get() = session?.cards?.filterIsInstance<Card.ColdOpen>()?.firstOrNull()

    /** True when the plan has loaded and offers neither a review nor a lesson: only a song, or nothing. */
    val caughtUp: Boolean get() = session != null && session.cards.none { it !is Card.Song }
}

/** Where a tapped card leads. */
public sealed interface TodayTarget {
    /** Practice [passageId] on [device] at [rung], in [mode] (a cold open performs). */
    public data class Practice(
        val device: MidiDevice,
        val passageId: String,
        val rung: TempoRung,
        val mode: RunMode = RunMode.PRACTICE,
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

    /** No piano is connected: the Pianos screen. */
    public data object Pianos : TodayTarget
}
