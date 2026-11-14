package pianoanchor.feature.practice

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Whether the metronome clicks, kept for the app session (there is no settings store yet): one instance is shared by
 * every [PracticeViewModel], so the choice survives leaving and reopening the practice screen.
 */
public class ClickSetting(
    initiallyOn: Boolean = true,
) {
    /** True while the click is on. */
    public val on: MutableStateFlow<Boolean> = MutableStateFlow(initiallyOn)
}
