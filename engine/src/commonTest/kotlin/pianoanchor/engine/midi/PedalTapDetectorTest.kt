package pianoanchor.engine.midi

import pianoanchor.core.model.ControlChange
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOn
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val MS = 1_000_000L

class PedalTapDetectorTest {
    private fun cc(
        ms: Long,
        value: Int,
        controller: Int = 64,
    ) = ControlChange(ms * MS, 0, controller, value)

    @Test
    fun aQuickDownAndUpIsATap() {
        val detector = PedalTapDetector()
        assertFalse(detector.onEvent(cc(0, 127)))
        assertTrue(detector.onEvent(cc(200, 0)))
    }

    @Test
    fun aHoldLongerThanTheLimitIsNotATap() {
        val detector = PedalTapDetector()
        detector.onEvent(cc(0, 127))
        assertFalse(detector.onEvent(cc(900, 0)))
    }

    @Test
    fun aHalfPedalWobbleCountsOnce() {
        val detector = PedalTapDetector()
        val taps = listOf(cc(0, 70), cc(50, 40), cc(100, 70), cc(150, 0)).count { detector.onEvent(it) }
        kotlin.test.assertEquals(1, taps)
    }

    @Test
    fun anUpWithoutADownIsNotATap() {
        assertFalse(PedalTapDetector().onEvent(cc(0, 0)))
    }

    @Test
    fun aShallowPressNeverCounts() {
        val detector = PedalTapDetector()
        detector.onEvent(cc(0, 40))
        assertFalse(detector.onEvent(cc(100, 0)))
    }

    @Test
    fun otherControllersAndNotesAreNotTaps() {
        val detector = PedalTapDetector()
        detector.onEvent(cc(0, 127, controller = 67))
        assertFalse(detector.onEvent(cc(100, 0, controller = 67)))
        assertFalse(detector.onEvent(NoteOn(0, 0, MidiNote(60), 90)))
    }

    @Test
    fun resetMidPressForgetsIt() {
        val detector = PedalTapDetector()
        detector.onEvent(cc(0, 127))
        detector.reset()
        assertFalse(detector.onEvent(cc(100, 0)))
    }
}
