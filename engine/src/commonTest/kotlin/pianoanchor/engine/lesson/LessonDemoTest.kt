package pianoanchor.engine.lesson

import pianoanchor.core.model.TryCheck
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Skills
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LessonDemoTest {
    private fun notes(demo: Demo) = demo.tones.map { it.note.number }

    @Test
    fun anyKeyHasNothingToHear() = assertEquals(Demo(emptyList()), LessonDemo.of(TryCheck.AnyKey))

    @Test
    fun playKeyPlaysTheNote() = assertEquals(listOf(60), notes(LessonDemo.of(TryCheck.PlayKey(60, lit = false))))

    @Test
    fun findAllPlaysEveryTargetLowToHigh() {
        val demo = LessonDemo.of(TryCheck.FindAll(0, 21..108))
        assertEquals(listOf(24, 36, 48, 60, 72, 84, 96, 108), notes(demo))
        assertEquals(listOf(0L, 500L), demo.tones.take(2).map { it.startMs })
    }

    @Test
    fun sequencePlaysTheNotesInOrder() =
        assertEquals(listOf(60, 62, 64), notes(LessonDemo.of(TryCheck.Sequence(listOf(60, 62, 64)))))

    @Test
    fun repeatPlaysOneNotePerClickAfterTheLead() {
        val demo = LessonDemo.of(TryCheck.Repeat(60, times = 8, bpm = 60))
        assertEquals(60, demo.clickBpm)
        assertEquals(1000L, demo.leadMs)
        assertEquals(8, demo.tones.size)
        assertEquals(listOf(1000L, 2000L), demo.tones.take(2).map { it.startMs })
    }

    @Test
    fun rhythmPlaysThePatternAfterOneBeat() {
        val demo = LessonDemo.of(TryCheck.Rhythm(60, listOf(1440, 480, 1920), bpm = 60))
        assertEquals(60, demo.clickBpm)
        assertEquals(1000L, demo.leadMs)
        assertEquals(listOf(1000L, 2500L, 3000L), demo.tones.map { it.startMs })
        assertEquals(listOf(750L, 250L, 1000L), demo.tones.map { it.lengthMs })
        assertEquals(setOf(60), demo.tones.map { it.note.number }.toSet())
    }

    @Test
    fun holdSoundsForItsClicks() {
        val demo = LessonDemo.of(TryCheck.Hold(60, clicks = 2, bpm = 60))
        assertEquals(2000L, demo.tones.single().lengthMs)
        assertEquals(60, demo.clickBpm)
    }

    @Test
    fun stressAccentsBeatOneByVelocity() {
        val demo = LessonDemo.of(TryCheck.StressBeatOne(60, bars = 2, bpm = 60))
        assertEquals(8, demo.tones.size)
        assertEquals(
            listOf(100, 40, 40, 40, 100),
            demo.tones.take(5).map { it.velocity },
        )
    }

    @Test
    fun softThenLoudUsesTheTwoDemoVelocities() {
        val demo = LessonDemo.of(TryCheck.SoftThenLoud(60))
        assertEquals(listOf(40, 100), demo.tones.map { it.velocity })
        assertNull(demo.clickBpm)
        assertEquals(Tunables.PLAYBACK_NOTE_GAP_MS, demo.tones.last().startMs)
    }

    @Test
    fun aChordSoundsTogether() {
        val demo = LessonDemo.of(TryCheck.Chord(setOf(67, 60, 64)))
        assertEquals(listOf(60, 64, 67), notes(demo))
        assertTrue(demo.tones.all { it.startMs == 0L })
    }

    @Test
    fun aPassageIsPlayedAtItsWrittenTempo() {
        val passage = Curriculum.passages.first { Skills.FINGER_NUMBER_TUNES.id in it.skills }
        val demo = LessonDemo.of(TryCheck.Passage(passage.id))
        assertEquals(passage.steps.sumOf { it.notes.size }, demo.tones.size)
        assertEquals(0L, demo.tones.first().startMs)
    }

    @Test
    fun anUnknownPassageHasNothingToHear() = assertEquals(emptyList(), LessonDemo.of(TryCheck.Passage("nope")).tones)

    @Test
    fun legatoPlaysEachNoteForAFullBeatIntoTheNext() {
        val demo = LessonDemo.of(TryCheck.Legato(listOf(60, 62, 64)))
        assertEquals(listOf(60, 62, 64), notes(demo))
        assertEquals(listOf(0L, 1000L, 2000L), demo.tones.map { it.startMs })
        assertEquals(listOf(1000L, 1000L, 1000L), demo.tones.map { it.lengthMs })
        assertNull(demo.clickBpm)
        assertTrue(demo.pedal.isEmpty())
    }

    @Test
    fun pedalHoldPlaysTheChordForABeatAndLiftsThePedalAfterTheRing() {
        val demo = LessonDemo.of(TryCheck.PedalHold(setOf(55, 48, 52)))
        assertEquals(listOf(48, 52, 55), notes(demo))
        assertTrue(demo.tones.all { it.startMs == 0L && it.lengthMs == 1000L })
        assertEquals(listOf(PedalCue(0, true), PedalCue(1000 + Tunables.TRY_PEDAL_RING_MS, false)), demo.pedal)
        assertNull(demo.clickBpm)
    }

    @Test
    fun pedalChangePlaysEachChordForTwoBeatsWithAnUpDownCueAfterEachNewOne() {
        val demo = LessonDemo.of(TryCheck.PedalChange(listOf(setOf(48, 52), setOf(53, 57))))
        assertEquals(listOf(48, 52, 53, 57), notes(demo))
        assertEquals(listOf(0L, 0L, 2000L, 2000L), demo.tones.map { it.startMs })
        assertTrue(demo.tones.all { it.lengthMs == 2000L })
        val cluster = Tunables.CHORD_CLUSTER_MS.toLong()
        assertEquals(
            listOf(PedalCue(0, true), PedalCue(2000 + cluster, false), PedalCue(2000 + 2 * cluster, true)),
            demo.pedal,
        )
    }
}
