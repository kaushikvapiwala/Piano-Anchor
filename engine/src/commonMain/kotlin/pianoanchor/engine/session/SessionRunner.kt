package pianoanchor.engine.session

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
     * The session's cards as steps, in run order: warm-up (the cold open, then the quizzes), learn, practise, ear,
     * sight-read. Cards whose index in [Session.cards] is in [skipped] are left out. An empty session gives an empty
     * list.
     */
    public fun steps(
        session: Session,
        skipped: Set<Int> = emptySet(),
    ): List<SessionStep> =
        session.cards
            .filterIndexed { index, _ -> index !in skipped }
            .map(::stepOf)
            .sortedBy(::runOrder)

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
