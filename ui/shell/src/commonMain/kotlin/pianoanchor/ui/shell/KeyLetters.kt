package pianoanchor.ui.shell

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pianoanchor.engine.Tunables

/** How a key strip's letters appear: a fade over `Tunables.KEY_LETTERS_FADE_MS`, at once with [reduceMotion]. */
fun <T> keyLettersFade(reduceMotion: Boolean): AnimationSpec<T> =
    if (reduceMotion) snap() else tween(Tunables.KEY_LETTERS_FADE_MS, easing = FastOutSlowInEasing)

/**
 * How visible a key strip's letter layer is: 0 until the note labels have loaded ([loaded]), then fading in over
 * `Tunables.KEY_LETTERS_FADE_MS` (at once with [reduceMotion]). A strip that is already loaded when it first
 * composes shows its letters at once, so only letters that arrive later fade in. Read the state's value only
 * inside `graphicsLayer { }`, so the fade redraws without recomposing every key.
 */
@Composable
fun rememberLetterAlpha(
    loaded: Boolean,
    reduceMotion: Boolean = rememberReduceMotion(),
): State<Float> = animateFloatAsState(if (loaded) 1f else 0f, keyLettersFade(reduceMotion))

/** The letter size before it is capped by the key: 12 sp, so it grows with the learner's font scale. */
private val LETTER_SIZE = 12.sp

/** The most of a key's width a letter's height may take (`docs/ui/living-studio.md`, "Key strip"). */
private const val MAX_LETTER_SHARE = 0.4f

/** The narrowest black key that has room for the flat name under the sharp name. */
private val TWO_NAMES_WIDTH = 30.dp

/** Contrast of normal text the letters must keep against their key (WCAG AA). */
private const val MIN_CONTRAST = 4.5f

private const val LUMINANCE_OFFSET = 0.05f

private val DarkText = Color(0xFF1B1B1F)

/** The WCAG contrast ratio of two colours, 1 to 21. */
internal fun contrast(
    a: Color,
    b: Color,
): Float {
    val (light, dark) = if (a.luminance() >= b.luminance()) a to b else b to a
    return (light.luminance() + LUMINANCE_OFFSET) / (dark.luminance() + LUMINANCE_OFFSET)
}

/**
 * The colour of a letter on a key filled with [fill]: [preferred] when it keeps [MIN_CONTRAST] against the fill,
 * else white or near-black, whichever contrasts more, so the letter reads on every idle, lit and glowing key in both
 * themes.
 */
fun keyLetterColor(
    fill: Color,
    preferred: Color,
): Color =
    if (contrast(preferred, fill) >= MIN_CONTRAST) {
        preferred
    } else if (contrast(Color.White, fill) >= contrast(DarkText, fill)) {
        Color.White
    } else {
        DarkText
    }

@Composable
private fun letterSize(keyWidth: Dp): TextUnit =
    with(LocalDensity.current) {
        minOf(LETTER_SIZE.toPx(), (keyWidth * MAX_LETTER_SHARE).toPx()).toSp()
    }

private fun namesOn(
    sharp: String,
    flat: String?,
    keyWidth: Dp,
): List<String> = listOfNotNull(sharp, flat.takeIf { keyWidth >= TWO_NAMES_WIDTH })

/**
 * The height of the letters [KeyLetters] writes on a key [keyWidth] wide, at the current font scale: a strip makes
 * a coloured band under the letters at least this tall (plus its padding) so every line sits on the band.
 */
@Composable
fun keyLettersHeight(
    sharp: String,
    flat: String?,
    keyWidth: Dp,
): Dp {
    val size = letterSize(keyWidth)
    return with(LocalDensity.current) { size.toDp() * namesOn(sharp, flat, keyWidth).size }
}

/**
 * The letter(s) written at the bottom of a key [keyWidth] wide: [sharp] (a white key's letter or a black key's sharp
 * name), with [flat] under it when the key is wide enough. Inter 500 at 12 sp, at most 40% of the key's width. Hidden
 * from TalkBack: the key already carries its name.
 */
@Composable
fun KeyLetters(
    sharp: String,
    flat: String?,
    color: Color,
    keyWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val size = letterSize(keyWidth)
    Column(modifier.fillMaxWidth().clearAndSetSemantics {}) {
        for (name in namesOn(sharp, flat, keyWidth)) {
            Text(
                name,
                color = color,
                fontSize = size,
                lineHeight = size,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
