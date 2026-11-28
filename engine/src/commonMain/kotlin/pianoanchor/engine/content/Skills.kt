package pianoanchor.engine.content

import pianoanchor.core.model.Skill
import pianoanchor.core.model.Track

/**
 * Skills built into the app, one per concept of the beginner course (`docs/course/beginner-course.md`), in map order.
 * Titles live here, like passage titles, and are localised with them later. Each course skill needs the one before
 * it; that only suggests the next lesson and never locks anything. Then the optional "More songs" skills, which
 * also wait for the last concept.
 */
public object Skills {
    /** Sitting at the piano, curving the hand and numbering the fingers 1 to 5. */
    public val HAND_SHAPE: Skill = Skill("hand-shape", "Sit, hand shape, fingers 1–5", emptySet())

    /** Finding middle C, the C nearest the middle of the keyboard, home base for everything else. */
    public val MIDDLE_C: Skill = Skill("middle-c", "Middle C", setOf(HAND_SHAPE.id))

    /** Telling the groups of two and three black keys apart, to find a place without a light. */
    public val BLACK_KEY_GROUPS: Skill = Skill("black-key-groups", "Black keys, groups of 2 and 3", setOf(MIDDLE_C.id))

    /** Telling high from low and finding every C by the group of two black keys above it. */
    public val HIGH_AND_LOW: Skill = Skill("high-and-low", "High and low, all the Cs", setOf(BLACK_KEY_GROUPS.id))

    /** Playing one note to each click of a steady beat. */
    public val STEADY_BEAT: Skill = Skill("steady-beat", "Steady beat", setOf(HIGH_AND_LOW.id))

    /** Holding a note for one click (quarter) or two (half). */
    public val QUARTER_AND_HALF: Skill = Skill("quarter-and-half", "Quarter and half notes", setOf(STEADY_BEAT.id))

    /** Naming the white keys from the black-key groups. */
    public val WHITE_KEY_NAMES: Skill = Skill("white-key-names", "C D E, then F G A B", setOf(QUARTER_AND_HALF.id))

    /** Playing C to G with the right hand, thumb on middle C, one finger per key. */
    public val RH_C_POSITION: Skill = Skill("rh-c-position", "Right-hand C position", setOf(WHITE_KEY_NAMES.id))

    /** Moving to the next key (a step) or jumping one key (a skip). */
    public val STEPS_AND_SKIPS: Skill = Skill("steps-and-skips", "Steps and skips", setOf(RH_C_POSITION.id))

    /** Playing whole tunes inside the five-finger position from finger numbers. */
    public val FINGER_NUMBER_TUNES: Skill =
        Skill("finger-number-tunes", "First tunes by finger number", setOf(STEPS_AND_SKIPS.id))

    /** Counting four beats to the bar and feeling beat 1 after each bar line. */
    public val BARS_AND_COUNTING: Skill =
        Skill("bars-and-counting", "Bar lines, counting to 4", setOf(FINGER_NUMBER_TUNES.id))

    /** Reading steps on the staff upward from middle C. */
    public val STAFF_STEPS: Skill = Skill("staff-steps", "The staff, middle C, steps", setOf(BARS_AND_COUNTING.id))

    /** Reading the treble clef from its G landmark, including skips. */
    public val TREBLE_CLEF: Skill = Skill("treble-clef", "Treble clef = G, skips", setOf(STAFF_STEPS.id))

    /** Playing a phrase softly (p) and loudly (f). */
    public val SOFT_AND_LOUD: Skill = Skill("soft-and-loud", "Soft and loud (p, f)", setOf(TREBLE_CLEF.id))

    /** Playing C to G with the left hand, little finger on the C below middle C. */
    public val LH_C_POSITION: Skill = Skill("lh-c-position", "Left-hand C position", setOf(SOFT_AND_LOUD.id))

    /** Reading the bass clef from its F landmark and holding a whole note for four beats. */
    public val BASS_CLEF_WHOLE_NOTE: Skill =
        Skill("bass-clef-whole-note", "Bass clef = F, whole note", setOf(LH_C_POSITION.id))

    /** Reading both staves, playing a phrase with one hand and then the other, and counting rests. */
    public val GRAND_STAFF_TURNS_RESTS: Skill =
        Skill("grand-staff-turns-rests", "Grand staff, hands taking turns, rests", setOf(BASS_CLEF_WHOLE_NOTE.id))

    /** Counting three beats to the bar and holding a dotted half note. */
    public val THREE_FOUR_TIME: Skill =
        Skill("three-four-time", "3/4 time, dotted half note", setOf(GRAND_STAFF_TURNS_RESTS.id))

    /** Keeping a left-hand note down while the right hand plays the tune. */
    public val HANDS_TOGETHER_HELD: Skill =
        Skill("hands-together-held", "Hands together, left hand holding", setOf(THREE_FOUR_TIME.id))

    /** Playing C, E and G together with fingers 1, 3 and 5. */
    public val C_CHORD: Skill = Skill("c-chord", "C chord, fingers 1-3-5", setOf(HANDS_TOGETHER_HELD.id))

    /** Playing G and F chords and changing chord on beat 1. */
    public val G_AND_F_CHORDS: Skill = Skill("g-and-f-chords", "G and F chords, chord symbols", setOf(C_CHORD.id))

    /** Playing a right-hand melody over left-hand chords. */
    public val MELODY_AND_CHORDS: Skill = Skill("melody-and-chords", "Melody plus chords", setOf(G_AND_F_CHORDS.id))

    /** Playing the black key just above (sharp) or below (flat) a white key. */
    public val SHARPS_AND_FLATS: Skill = Skill("sharps-and-flats", "Sharps and flats", setOf(MELODY_AND_CHORDS.id))

    /** Playing two even notes in one click, counted "1 and". */
    public val EIGHTH_NOTES: Skill =
        Skill("eighth-notes", "Eighth notes", setOf(BARS_AND_COUNTING.id, MELODY_AND_CHORDS.id))

    /** Reading beamed eighth pairs mixed with quarters in tunes. */
    public val EIGHTH_PAIRS: Skill = Skill("eighth-pairs", "Eighth pairs in tunes", setOf(EIGHTH_NOTES.id))

    /** Counting a dotted quarter (one and a half clicks) followed by an eighth. */
    public val DOTTED_QUARTER: Skill =
        Skill("dotted-quarter", "Dotted quarter and eighth", setOf(EIGHTH_PAIRS.id))

    /** Playing the Jingle Bells chorus, which puts eighths and the dotted quarter together over left-hand chords. */
    public val RHYTHM_SONG: Skill =
        Skill("rhythm-song", "A song with eighths", setOf(DOTTED_QUARTER.id, MELODY_AND_CHORDS.id))

    /** Playing G A B C D with the right hand, thumb on the G above middle C. */
    public val RH_G_POSITION: Skill =
        Skill("rh-g-position", "Right-hand G position", setOf(RH_C_POSITION.id, RHYTHM_SONG.id))

    /** Playing G A B C D with the left hand, little finger on the low G. */
    public val LH_G_POSITION: Skill =
        Skill("lh-g-position", "Left-hand G position", setOf(LH_C_POSITION.id, RH_G_POSITION.id))

    /** Reading the one sharp (F sharp) of the key signature of G. */
    public val KEY_OF_G: Skill =
        Skill("key-of-g", "The key of G", setOf(SHARPS_AND_FLATS.id, RH_G_POSITION.id))

    /** Playing the G chord and the D7 shortcut (F sharp, C, D) with the left hand. */
    public val G_AND_D7_CHORDS: Skill =
        Skill("g-and-d7-chords", "G and D7 chords", setOf(LH_G_POSITION.id, KEY_OF_G.id))

    /** Playing Go Tell Aunt Rhody with G and D7 chords under the melody. */
    public val MELODY_CHORDS_IN_G: Skill =
        Skill("melody-chords-in-g", "Melody plus chords in G", setOf(G_AND_D7_CHORDS.id))

    /** Playing notes joined: each key goes down as the one before comes up. */
    public val LEGATO_TOUCH: Skill =
        Skill("legato-touch", "Legato touch", setOf(RH_C_POSITION.id, MELODY_CHORDS_IN_G.id))

    /** Pressing the sustain pedal before playing and lifting it to stop the sound. */
    public val SUSTAIN_PEDAL: Skill =
        Skill("sustain-pedal", "The sustain pedal", setOf(LEGATO_TOUCH.id, C_CHORD.id))

    /** Lifting and pressing the pedal again right after each new chord. */
    public val PEDAL_CHANGES: Skill =
        Skill("pedal-changes", "Changing the pedal", setOf(SUSTAIN_PEDAL.id, G_AND_F_CHORDS.id))

    /** Playing Amazing Grace in 3/4 with a pedal change every bar. */
    public val PEDAL_SONG: Skill =
        Skill("pedal-song", "A song with pedal", setOf(PEDAL_CHANGES.id, THREE_FOUR_TIME.id))

    /** Passing the thumb under the hand so a line can climb past finger 5. */
    public val THUMB_UNDER: Skill =
        Skill("thumb-under", "Thumb under", setOf(RH_C_POSITION.id, PEDAL_SONG.id))

    /** Playing the C major scale, one octave up and down, with the right hand. */
    public val C_MAJOR_SCALE: Skill = Skill("c-major-scale", "The C major scale", setOf(THUMB_UNDER.id))

    /** Playing the G major scale, one octave up and down, with the right hand. */
    public val G_MAJOR_SCALE: Skill =
        Skill("g-major-scale", "The G major scale", setOf(C_MAJOR_SCALE.id, KEY_OF_G.id))

    /** Playing the C major scale, one octave up and down, with the left hand. */
    public val LH_C_SCALE: Skill =
        Skill("lh-c-scale", "Left-hand C scale", setOf(C_MAJOR_SCALE.id, LH_C_POSITION.id))

    /** Playing the opening of Joy to the World, which runs down the C major scale. */
    public val SCALE_SONG: Skill =
        Skill("scale-song", "A song with a scale", setOf(C_MAJOR_SCALE.id, DOTTED_QUARTER.id))

    /** Playing F and G in first inversion, so the left hand stays close to C. */
    public val MORE_INVERSIONS: Skill =
        Skill(
            "more-inversions",
            "F and G in first inversion",
            setOf(MELODY_AND_CHORDS.id, SHARPS_AND_FLATS.id),
            Track.SONGS,
        )

    /** Playing the A minor triad. */
    public val MORE_A_MINOR: Skill =
        Skill("more-a-minor", "A minor triad", setOf(C_CHORD.id, SHARPS_AND_FLATS.id), Track.SONGS)

    /** Playing the I–V\u2013vi–IV progression (C, G, Am, F) under a melody. */
    public val MORE_FOUR_CHORDS: Skill =
        Skill(
            "more-four-chords",
            "I–V\u2013vi–IV progression",
            setOf(MORE_INVERSIONS.id, MORE_A_MINOR.id),
            Track.SONGS,
        )
}
