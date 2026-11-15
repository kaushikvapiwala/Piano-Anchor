package pianoanchor.app

import pianoanchor.core.model.BarRange
import pianoanchor.core.model.RunMode
import pianoanchor.engine.matcher.TempoRung

/** What a Practice opens with (one Koin parameter): its [rung], [mode] and the [spot] bars to loop (null: all). */
data class PracticeStart(
    val rung: TempoRung,
    val mode: RunMode,
    val spot: BarRange? = null,
)
