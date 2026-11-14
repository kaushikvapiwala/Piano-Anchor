package pianoanchor.engine.scheduler

import pianoanchor.core.model.Rating

/** One review: the inputs and what py-fsrs produced. [retrievability] is null for the first review of a sequence. */
internal data class FsrsVectorStep(
    val rating: Rating,
    val gapDays: Int,
    val stability: Double,
    val difficulty: Double,
    val retrievability: Double?,
    val intervalDays: Int,
)
