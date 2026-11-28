package pianoanchor.engine.content

import kotlinx.datetime.LocalDate
import pianoanchor.core.model.ChordMark
import pianoanchor.core.model.ChordQuality
import pianoanchor.core.model.KeySignature
import pianoanchor.core.model.Passage
import pianoanchor.core.model.Rights
import pianoanchor.core.model.RightsStatus
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.core.model.TimeSignature

internal val FOUR_FOUR = TimeSignature(beats = 4, beatUnit = 4)
internal val THREE_FOUR = TimeSignature(beats = 3, beatUnit = 4)

private val CHECKED_ON = LocalDate(year = 2026, month = 11, day = 15)

/** The rights record of an exercise written for Piano Anchor; [what] says what it practises. */
internal fun exerciseRights(what: String): Rights =
    Rights(
        status = RightsStatus.ORIGINAL,
        origin = "Piano Anchor, 2026",
        basis = "An exercise on $what, written for Piano Anchor.",
        checkedOn = CHECKED_ON,
    )

/** The rights record of a piece written for Piano Anchor; [what] says what it is. */
internal fun originalPieceRights(what: String): Rights =
    Rights(
        status = RightsStatus.ORIGINAL,
        origin = "Piano Anchor, 2026",
        basis = "$what, an original piece written for Piano Anchor.",
        checkedOn = CHECKED_ON,
    )

/** The rights record of a public domain tune: [origin] names the work, [basis] says why it is free to use. */
internal fun publicDomainRights(
    origin: String,
    basis: String,
): Rights =
    Rights(
        status = RightsStatus.PUBLIC_DOMAIN,
        origin = origin,
        basis = basis,
        checkedOn = CHECKED_ON,
    )

/** A built-in passage whose notes are note text (see `NoteText`) in [timeSignature]. */
internal fun builtinPassage(
    id: String,
    title: String,
    notes: String,
    tempoBpm: Int,
    skills: Set<String>,
    rights: Rights,
    timeSignature: TimeSignature = FOUR_FOUR,
    piece: Boolean = false,
    keySignature: KeySignature = KeySignature.C,
    chords: String = "",
): Passage =
    Passage(
        id = "builtin:$id",
        title = title,
        steps = NoteText.parse(notes, timeSignature),
        timeSignature = timeSignature,
        tempoBpm = tempoBpm,
        rights = rights,
        skills = skills,
        piece = piece,
        keySignature = keySignature,
        chords = chordLine(chords, timeSignature),
    )

/** Pitch classes of the chord letters a chord line may use. */
private val CHORD_ROOTS = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)

/**
 * A chord line from text (`docs/algorithms/play-along.md`): bars separated by `|`, the chords of a bar by spaces,
 * each a letter C..B with an optional `m` (minor) or `7` (dominant seventh). A bar's chords share it evenly, so a bar
 * of two chords changes at its middle. Blank text is no chord line. Throws [IllegalArgumentException] for a chord it
 * can't read or a bar that its chords do not divide into whole beats.
 */
internal fun chordLine(
    text: String,
    timeSignature: TimeSignature,
): List<ChordMark> =
    if (text.isBlank()) {
        emptyList()
    } else {
        text.split('|').withIndex().flatMap { (bar, chords) ->
            val names = chords.trim().split(Regex("\\s+"))
            val share = timeSignature.barTicks / names.size
            require(share * names.size == timeSignature.barTicks && share % TICKS_PER_QUARTER == 0) {
                "bar ${bar + 1} cannot give ${names.size} chords a whole number of beats each"
            }
            names.mapIndexed { i, name -> chordMark(bar * timeSignature.barTicks + i * share, name) }
        }
    }

private fun chordMark(
    onsetTicks: Int,
    name: String,
): ChordMark {
    val root = requireNotNull(CHORD_ROOTS[name.firstOrNull()]) { "unknown chord \"$name\"" }
    val quality =
        when (name.drop(1)) {
            "" -> ChordQuality.MAJOR
            "m" -> ChordQuality.MINOR
            "7" -> ChordQuality.DOMINANT_7
            else -> throw IllegalArgumentException("unknown chord \"$name\"")
        }
    return ChordMark(onsetTicks, root, quality)
}
