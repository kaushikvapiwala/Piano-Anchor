package pianoanchor.engine.lesson

import pianoanchor.core.model.NoteNames
import pianoanchor.engine.content.Skills

/** How many keys of a strip carry a letter (`docs/ui/living-studio.md`, "Key strip"). */
public enum class NoteNameStage { ALL_WHITE, TARGET_ONLY, NONE }

private const val SEMITONES = 12
private val BLACK_PITCH_CLASSES = setOf(1, 3, 6, 8, 10)

// Pitch class → letter and, for a black key, the letter below it (its sharp) and above it (its flat).
private val SHARP_LETTERS =
    listOf(
        Letter.C,
        Letter.C,
        Letter.D,
        Letter.D,
        Letter.E,
        Letter.F,
        Letter.F,
        Letter.G,
        Letter.G,
        Letter.A,
        Letter.A,
        Letter.B,
    )
private val FLAT_LETTERS =
    listOf(
        Letter.C,
        Letter.D,
        Letter.D,
        Letter.E,
        Letter.E,
        Letter.F,
        Letter.G,
        Letter.G,
        Letter.A,
        Letter.A,
        Letter.B,
        Letter.B,
    )

/** A natural letter name of a key. */
public enum class Letter { C, D, E, F, G, A, B }

/** Whether a [KeyName] is a plain letter, sharpened or flattened. */
public enum class KeyAccidental { NATURAL, SHARP, FLAT }

/** A key's name as data: [letter] plus [accidental]; the UI turns it into text. */
public data class KeyName(
    val letter: Letter,
    val accidental: KeyAccidental,
)

/**
 * Which keys of a keyboard strip get a letter, so a beginner can match screen to piano and the letters fade as the
 * key names are learned. Pure. The stages hang on two course skills: [Skills.WHITE_KEY_NAMES] and
 * [Skills.SHARPS_AND_FLATS].
 */
public object NoteNameRule {
    /** The stage: ALL_WHITE until key names are passed, TARGET_ONLY until Learned, then NONE; OFF → NONE. */
    public fun stage(
        setting: NoteNames,
        passed: Set<String>,
        learned: Set<String>,
    ): NoteNameStage =
        when {
            setting == NoteNames.OFF -> NoteNameStage.NONE
            Skills.WHITE_KEY_NAMES.id !in passed -> NoteNameStage.ALL_WHITE
            Skills.WHITE_KEY_NAMES.id !in learned -> NoteNameStage.TARGET_ONLY
            else -> NoteNameStage.NONE
        }

    /** Whether the learner has passed sharps and flats, from which black keys get names. */
    public fun sharpsPassed(passed: Set<String>): Boolean = Skills.SHARPS_AND_FLATS.id in passed

    /**
     * The name of [note] at [stage], or null: white keys get their letter; black keys get their sharp name (C sharp)
     * only once [sharpsPassed]; [target] notes keep their label at TARGET_ONLY; [wrongHint] notes always get one
     * (OFF is the caller's: it passes no hint notes).
     */
    public fun label(
        note: Int,
        stage: NoteNameStage,
        target: Set<Int>,
        wrongHint: Set<Int>,
        sharpsPassed: Boolean,
    ): KeyName? {
        val shown =
            note in wrongHint ||
                when (stage) {
                    NoteNameStage.ALL_WHITE -> true
                    NoteNameStage.TARGET_ONLY -> note in target
                    NoteNameStage.NONE -> false
                }
        val pitchClass = note.mod(SEMITONES)
        val black = pitchClass in BLACK_PITCH_CLASSES
        val accidental = if (black) KeyAccidental.SHARP else KeyAccidental.NATURAL
        return KeyName(SHARP_LETTERS[pitchClass], accidental).takeIf { shown && (!black || sharpsPassed) }
    }

    /**
     * The flat name of black key [note] (a [KeyName] with [KeyAccidental.FLAT]), shown under its sharp name on a wide
     * enough key; null for a white key.
     */
    public fun flatLabel(note: Int): KeyName? =
        (
            note.mod(
                SEMITONES,
            )
        ).takeIf { it in BLACK_PITCH_CLASSES }?.let { KeyName(FLAT_LETTERS[it], KeyAccidental.FLAT) }
}

/**
 * What a strip needs to letter its keys: the [stage], whether black keys are named ([sharpsPassed]) and whether a
 * wrong-key hint letters its keys ([hints]; false when the learner turned note names off).
 */
public data class NoteLabels(
    val stage: NoteNameStage = NoteNameStage.NONE,
    val sharpsPassed: Boolean = false,
    val hints: Boolean = false,
) {
    /** The label of [note] while [wrongHint] keys are shown, or null: [NoteNameRule.label] with these labels. */
    public fun label(
        note: Int,
        target: Set<Int> = emptySet(),
        wrongHint: Set<Int> = emptySet(),
    ): KeyName? = NoteNameRule.label(note, stage, target, if (hints) wrongHint else emptySet(), sharpsPassed)

    public companion object {
        /** The labels for a learner whose settings say [setting] and whose progress is [passed] and [learned]. */
        public fun of(
            setting: NoteNames,
            passed: Set<String>,
            learned: Set<String>,
        ): NoteLabels =
            NoteLabels(
                NoteNameRule.stage(setting, passed, learned),
                NoteNameRule.sharpsPassed(passed),
                setting == NoteNames.AUTO,
            )
    }
}
