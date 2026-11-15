package pianoanchor.engine.onboarding

import pianoanchor.core.model.NoteOn
import pianoanchor.engine.Tunables

private const val SEMITONES_PER_OCTAVE = 12

/** The pitch classes of the chord the onboarding plays: C, E and G. */
private val ECHO_PITCH_CLASSES = listOf(0, 4, 7)

/**
 * True when [played] (NoteOns in order) has the pitch classes C, E, G in that order, ignoring octave, with at most
 * one wrong note before C. Notes after the G don't matter.
 */
public fun echoMatches(played: List<NoteOn>): Boolean =
    (0..Tunables.ONBOARDING_ECHO_MAX_STRAY_NOTES).any { strays ->
        val notes = pitchClasses(played).drop(strays)
        notes.size >= ECHO_PITCH_CLASSES.size && notes.take(ECHO_PITCH_CLASSES.size) == ECHO_PITCH_CLASSES
    }

/**
 * True while [played] could still become a match with more notes: it is a start of C, E, G after at most one stray
 * note. When it is false and [echoMatches] is too, the attempt is a miss.
 */
public fun echoCanStillMatch(played: List<NoteOn>): Boolean =
    (0..Tunables.ONBOARDING_ECHO_MAX_STRAY_NOTES).any { strays ->
        val notes = pitchClasses(played).drop(strays)
        notes.size <= ECHO_PITCH_CLASSES.size && notes == ECHO_PITCH_CLASSES.take(notes.size)
    }

private fun pitchClasses(played: List<NoteOn>) = played.map { it.note.number % SEMITONES_PER_OCTAVE }
