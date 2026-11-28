package pianoanchor.engine.session

import pianoanchor.engine.Tunables

/** One step of the one-tap session: a [card] run for about [minutes] (`docs/algorithms/session-composer.md`). */
public sealed interface SessionStep {
    public val card: Card
    public val minutes: Int

    /** The warm-up: the cold open or the review quizzes. */
    public data class WarmUp(
        override val card: Card,
        override val minutes: Int,
    ) : SessionStep

    /** The lesson of the first unpassed course concept. */
    public data class Learn(
        override val card: Card.Lesson,
        override val minutes: Int,
    ) : SessionStep

    /** The piece in progress. */
    public data class Practise(
        override val card: Card.Song,
        override val minutes: Int,
    ) : SessionStep

    /**
     * The play-along: today's song over a chord backing and the click (`docs/algorithms/play-along.md`). Its [card] is
     * the song card of the Practise step before it.
     */
    public data class PlayAlong(
        override val card: Card.Song,
        override val minutes: Int,
    ) : SessionStep

    /** The ear screen. */
    public data class Ear(
        override val card: Card.Ear,
        override val minutes: Int,
    ) : SessionStep

    /** The sight-reading exercise. */
    public data class SightRead(
        override val card: Card.SightReading,
        override val minutes: Int,
    ) : SessionStep
}

/** Turns Today's cards into the one-tap session's steps. Pure. */
public object SessionRunner {
    /**
     * The session's cards as steps, in run order: warm-up (the cold open, then the quizzes), learn, practise, play
     * along, ear, sight-read. A song whose passage has a chord line adds a play-along step right after its Practise
     * step, taking [Tunables.PLAY_ALONG_MINUTES] from the song's minutes (at most half of them, so Practise keeps
     * some).
     * Cards whose index in [Session.cards] is in [skipped] are left out (a skipped song drops both of its steps). An
     * empty session gives an empty list.
     */
    public fun steps(
        session: Session,
        skipped: Set<Int> = emptySet(),
    ): List<SessionStep> =
        session.cards
            .filterIndexed { index, _ -> index !in skipped }
            .flatMap(::stepsOf)
            .sortedBy(::runOrder)

    /** A song with chords splits into Practise and a play-along, unless its share is too short to leave a minute. */
    private fun stepsOf(card: Card): List<SessionStep> {
        val along = if (card is Card.Song && card.passage.chords.isNotEmpty()) playAlongMinutes(card) else 0
        return if (card is Card.Song && along > 0) {
            listOf(SessionStep.Practise(card, card.minutes - along), SessionStep.PlayAlong(card, along))
        } else {
            listOf(stepOf(card))
        }
    }

    private fun playAlongMinutes(song: Card.Song): Int = minOf(Tunables.PLAY_ALONG_MINUTES, song.minutes / 2)

    private fun stepOf(card: Card): SessionStep =
        when (card) {
            is Card.ColdOpen, is Card.Quizzes -> SessionStep.WarmUp(card, card.minutes)
            is Card.Lesson -> SessionStep.Learn(card, card.minutes)
            is Card.Song -> SessionStep.Practise(card, card.minutes)
            is Card.Ear -> SessionStep.Ear(card, card.minutes)
            is Card.SightReading -> SessionStep.SightRead(card, card.minutes)
        }

    /** The position of a step's kind in the run; the sort is stable, so a kind keeps its order. */
    private fun runOrder(step: SessionStep): Int =
        when (val card = step.card) {
            is Card.ColdOpen -> 0
            is Card.Quizzes -> 1
            else -> 2 + order.indexOf(card::class)
        }

    private val order = listOf(Card.Lesson::class, Card.Song::class, Card.Ear::class, Card.SightReading::class)
}
