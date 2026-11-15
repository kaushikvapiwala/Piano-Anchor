package pianoanchor.engine.content

import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.ScoreNote
import pianoanchor.core.model.Step
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.core.model.TimeSignature

/** Parser for the note text a passage's notes are written in; the grammar is in `docs/algorithms/note-text.md`. */
public object NoteText {
    /**
     * Parses note text into steps (ticks from 0); bars are checked against [timeSignature]. Throws
     * IllegalArgumentException naming the token (1-based index and text) on bad input.
     */
    public fun parse(
        text: String,
        timeSignature: TimeSignature,
    ): List<Step> {
        val tokens = tokenize(text)
        require(tokens.isNotEmpty()) { "note text is empty" }
        val barTicks = timeSignature.barTicks
        val steps = ArrayList<Step>()
        var position = 0
        var barStart = 0
        for (token in tokens) {
            if (token.text == BAR_LINE) {
                token.check(position - barStart == barTicks) {
                    "bar has ${position - barStart} ticks, expected $barTicks for $timeSignature"
                }
                barStart = position
            } else {
                val (notes, length) = token.parseEvent()
                if (notes.isNotEmpty()) steps += Step(position, notes)
                position += length
            }
        }
        val lastBar = position - barStart
        require(if (barStart == 0) lastBar % barTicks == 0 else lastBar == 0 || lastBar == barTicks) {
            "the last bar has ${if (barStart == 0) lastBar % barTicks else lastBar} ticks, expected $barTicks for " +
                "$timeSignature"
        }
        require(steps.isNotEmpty()) { "note text has no notes, only rests" }
        return steps
    }
}

/** A space-separated token (a chord with its spaces is one token) and its 1-based [index], for error messages. */
private class Token(
    val index: Int,
    val text: String,
) {
    inline fun check(
        ok: Boolean,
        message: () -> String,
    ) = require(ok) { "token $index '$text': ${message()}" }

    fun fail(message: String): Nothing = throw IllegalArgumentException("token $index '$text': $message")

    /** The notes (none for a rest) and the length in ticks. */
    fun parseEvent(): Pair<Set<ScoreNote>, Int> {
        if (text.startsWith(REST)) return emptySet<ScoreNote>() to length(text.drop(1))
        val pitches = ArrayList<Int>()
        val lengthText: String
        if (text.startsWith(CHORD_OPEN)) {
            val close = text.indexOf(CHORD_CLOSE)
            check(close > 1) { "empty chord" }
            text.substring(1, close).split(' ').forEach {
                val (pitch, rest) = pitchAt(it)
                check(rest.isEmpty()) { "give a chord one length after ']', not '$rest' on '$it'" }
                pitches += pitch
            }
            lengthText = text.substring(close + 1)
        } else {
            val (pitch, rest) = pitchAt(text)
            pitches += pitch
            lengthText = rest
        }
        check(pitches.toSet().size == pitches.size) { "the same pitch twice in a chord" }
        val length = length(lengthText)
        return pitches.mapTo(LinkedHashSet()) { ScoreNote(MidiNote(it), length) } to length
    }

    /** The MIDI pitch of the note name at the start of [name], and the text after it. */
    private fun pitchAt(name: String): Pair<Int, String> {
        check(name.firstOrNull() in 'A'..'G') { "unknown note name" }
        val match = PITCH_REGEX.find(name) ?: fail("expected an octave number after the note name")
        val (letter, accidental, octaveText) = match.destructured
        val octave = octaveText.toIntOrNull() ?: fail("octave $octaveText out of range $MIN_OCTAVE..$MAX_OCTAVE")
        check(octave in MIN_OCTAVE..MAX_OCTAVE) { "octave $octave out of range $MIN_OCTAVE..$MAX_OCTAVE" }
        val midi = (octave + 1) * SEMITONES + PITCH_CLASS.getValue(letter.single()) + ACCIDENTAL.getValue(accidental)
        check(midi in 0..MAX_MIDI) { "pitch $midi is outside MIDI 0..$MAX_MIDI" }
        return midi to name.substring(match.value.length)
    }

    private fun length(code: String): Int {
        val base = LENGTHS[code.removeSuffix(DOT)] ?: fail("unknown length '$code'")
        return if (code.endsWith(DOT)) base + base / 2 else base
    }
}

/** Splits on spaces, keeping a bracketed chord together as one token. */
private fun tokenize(text: String): List<Token> {
    val tokens = ArrayList<Token>()
    val parts = text.split(WHITESPACE).filter { it.isNotEmpty() }.iterator()
    while (parts.hasNext()) {
        var part = parts.next()
        val index = tokens.size + 1
        while (part.startsWith(CHORD_OPEN) && CHORD_CLOSE !in part) {
            require(parts.hasNext()) { "token $index '$part': chord is not closed with ']'" }
            part += " " + parts.next()
        }
        tokens += Token(index, part)
    }
    return tokens
}

private const val BAR_LINE = "|"
private const val REST = "r"
private const val DOT = "."
private const val CHORD_OPEN = '['
private const val CHORD_CLOSE = ']'
private const val SEMITONES = 12
private const val MIN_OCTAVE = -1
private const val MAX_OCTAVE = 9
private const val MAX_MIDI = 127
private const val WHOLE_TICKS = 4 * TICKS_PER_QUARTER
private val WHITESPACE = Regex("\\s+")
private val PITCH_REGEX = Regex("^([A-G])([#b]?)(-?\\d+)")
private val PITCH_CLASS = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)
private val ACCIDENTAL = mapOf("" to 0, "#" to 1, "b" to -1)
private val LENGTHS =
    mapOf(
        "w" to WHOLE_TICKS,
        "h" to WHOLE_TICKS / 2,
        "q" to WHOLE_TICKS / 4,
        "e" to WHOLE_TICKS / 8,
        "s" to WHOLE_TICKS / 16,
    )
