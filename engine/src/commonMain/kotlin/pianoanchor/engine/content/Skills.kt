package pianoanchor.engine.content

import pianoanchor.core.model.Skill
import pianoanchor.core.model.Track

/** Skills built into the app. Titles live here, like passage titles, and are localised with them later. */
public object Skills {
    /** Telling the groups of two and three black keys apart. */
    public val KB_BLACK_GROUPS: Skill = Skill("kb-black-groups", "Black keys in twos and threes", emptySet())

    /** Finding every C by the group of two black keys below it. */
    public val KB_FIND_C: Skill = Skill("kb-find-c", "Find every C", setOf(KB_BLACK_GROUPS.id))

    /** Finding middle C, the C nearest the middle of the keyboard. */
    public val KB_MIDDLE_C: Skill = Skill("kb-middle-c", "Middle C", setOf(KB_FIND_C.id))

    /** Playing single notes with the right hand on C, D and E, thumb on middle C. */
    public val RH_C_POSITION: Skill = Skill("rh-c-position", "Right hand, C position", setOf(KB_MIDDLE_C.id))

    /** Playing quarter notes on a steady beat. */
    public val RHYTHM_QUARTER: Skill = Skill("rhythm-quarter", "Quarter notes, steady beat", setOf(RH_C_POSITION.id))

    /** Moving the right hand to the next finger up or down. */
    public val RH_STEPS: Skill = Skill("rh-steps", "Right hand steps", setOf(RH_C_POSITION.id))

    /** Skipping a finger with the right hand (thirds). */
    public val RH_SKIPS: Skill = Skill("rh-skips", "Right hand skips (thirds)", setOf(RH_STEPS.id))

    /** Holding a note for two beats. */
    public val RHYTHM_HALF: Skill = Skill("rhythm-half", "Half notes", setOf(RHYTHM_QUARTER.id))

    /** Playing a whole tune inside the five-finger position. */
    public val RH_TUNES: Skill = Skill("rh-tunes", "Five-finger tunes", setOf(RH_SKIPS.id, RHYTHM_HALF.id))

    /** Keeping the beat steady at a faster tempo. */
    public val TEMPO_STEADY: Skill = Skill("tempo-steady", "Playing at tempo", setOf(RH_TUNES.id))

    /** Playing single notes with the left hand on C to G, little finger on the C below middle C. */
    public val LH_C_POSITION: Skill = Skill("lh-c-position", "Left hand, C position", setOf(RH_C_POSITION.id))

    /** Moving the left hand to the next finger up or down. */
    public val LH_STEPS: Skill = Skill("lh-steps", "Left hand steps", setOf(LH_C_POSITION.id))

    /** Holding a note for four beats. */
    public val RHYTHM_WHOLE: Skill = Skill("rhythm-whole", "Whole notes", setOf(RHYTHM_HALF.id))

    /** Playing a phrase with one hand, then the other. */
    public val HANDS_TURNS: Skill = Skill("hands-turns", "Hands taking turns", setOf(RH_STEPS.id, LH_STEPS.id))

    /** Counting three beats to the bar. */
    public val TIME_THREE_FOUR: Skill = Skill("time-three-four", "3/4 time", setOf(RHYTHM_HALF.id))

    /** Keeping a left-hand note down while the right hand plays the tune. */
    public val HANDS_HELD_BASS: Skill =
        Skill("hands-held-bass", "Held left-hand note under a tune", setOf(HANDS_TURNS.id, RHYTHM_WHOLE.id))

    /** Playing the C, F and G triads with the left hand in root position. */
    public val CHORD_TRIADS: Skill =
        Skill("chord-triads", "C, F and G triads (left hand)", setOf(HANDS_HELD_BASS.id), Track.SONGS)

    /** Changing between chords on the beat, in time. */
    public val CHORD_CHANGES: Skill =
        Skill("chord-changes", "Chord changes in time", setOf(CHORD_TRIADS.id, TEMPO_STEADY.id), Track.SONGS)

    /** Holding chords with the left hand under a right-hand melody. */
    public val CHORD_MELODY: Skill =
        Skill("chord-melody", "Melody over held chords", setOf(CHORD_CHANGES.id), Track.SONGS)

    /** Playing F and G in first inversion, so the left hand stays close to C. */
    public val CHORD_INVERSIONS: Skill =
        Skill("chord-inversions", "F and G in first inversion", setOf(CHORD_CHANGES.id), Track.SONGS)

    /** Playing the A minor triad. */
    public val CHORD_A_MINOR: Skill =
        Skill("chord-a-minor", "A minor triad", setOf(CHORD_TRIADS.id), Track.SONGS)

    /** Playing the I-V-vi-IV progression (C, G, Am, F) under a melody. */
    public val CHORD_FOUR: Skill =
        Skill(
            "chord-four",
            "I\u2013V\u2013vi\u2013IV progression",
            setOf(CHORD_INVERSIONS.id, CHORD_A_MINOR.id, CHORD_MELODY.id),
            Track.SONGS,
        )
}
