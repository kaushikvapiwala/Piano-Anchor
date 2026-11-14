package pianoanchor.feature.practice

/**
 * The pianos whose learner skipped the calibration card, kept for the app session (there is no settings store yet):
 * one instance is shared by every [PracticeViewModel], so the card doesn't come back for that piano until the app
 * restarts.
 */
public class CalibrationSkips {
    private val skipped = HashSet<String>()

    /** Whether the card was skipped for the piano with [deviceId] in this session. */
    public fun isSkipped(deviceId: String): Boolean = deviceId in skipped

    /** Remembers that the card was skipped for the piano with [deviceId]. */
    public fun skip(deviceId: String) {
        skipped += deviceId
    }
}
