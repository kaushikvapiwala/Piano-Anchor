package pianoanchor.engine.content

import kotlinx.datetime.LocalDate
import pianoanchor.core.model.KeySignature
import pianoanchor.core.model.Passage
import pianoanchor.core.model.Rights
import pianoanchor.core.model.RightsStatus
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

/** A built-in passage whose notes are note text (see `NoteText`) in [timeSignature] and [keySignature]. */
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
    )
