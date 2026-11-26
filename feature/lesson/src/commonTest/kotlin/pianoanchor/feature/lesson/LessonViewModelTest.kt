package pianoanchor.feature.lesson

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.LessonStep
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Skill
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Skills
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.lesson.CourseProgress
import pianoanchor.testing.FakeAttemptRepository
import pianoanchor.testing.FakeLearnerRepository
import pianoanchor.testing.FakeMetronome
import pianoanchor.testing.FakePiano
import pianoanchor.testing.FakeSettingsRepository
import pianoanchor.testing.FakeTone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val DEMO_DONE_MS = 20_000L

/** An input that counts the flows collected from it right now. */
private class CountingInput(
    private val delegate: MidiInputPort,
) : MidiInputPort by delegate {
    var open = 0
        private set

    override fun events(device: MidiDevice): Flow<MidiEvent> =
        delegate
            .events(device)
            .onStart { open++ }
            .onCompletion { open-- }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LessonViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val piano = FakePiano()
    private val input = CountingInput(piano)
    private val tone = FakeTone()
    private val metronome = FakeMetronome(piano.clock)
    private val settings = FakeSettingsRepository()
    private val attempts = FakeAttemptRepository()
    private val learnerRepository = FakeLearnerRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        skill: Skill = Skills.MIDDLE_C,
        startAt: LessonStep? = null,
        pedalIsMusic: Boolean = false,
    ): LessonViewModel =
        LessonViewModel(
            piano.device,
            skill.id,
            input,
            piano,
            tone,
            metronome,
            settings,
            attempts,
            LearnerModel(Curriculum.graph, learnerRepository),
            piano.clock,
            startAt,
            pedalIsMusic,
        ).also { runCurrent() }

    private suspend fun savedStep(skill: Skill = Skills.MIDDLE_C) = settings.settings.first().lessonSteps[skill.id]

    private fun TestScope.press(
        note: Int,
        velocity: Int = 64,
    ) {
        piano.press(MidiNote(note), velocity)
        runCurrent()
    }

    private fun TestScope.finishDemo() {
        advanceTimeBy(DEMO_DONE_MS)
        runCurrent()
    }

    private fun TestScope.toTry(vm: LessonViewModel) {
        vm.goTo(LessonStep.TRY)
        runCurrent()
    }

    @Test
    fun opensAtWhyWithNoSavedStep() =
        runTest(dispatcher) {
            val vm = viewModel()

            assertEquals(LessonStep.WHY, vm.state.value.step)
            assertEquals(Skills.MIDDLE_C, vm.state.value.skill)
            assertEquals(Curriculum.card(Skills.MIDDLE_C.id), vm.state.value.card)
            assertNull(vm.state.value.tryProgress)
            assertEquals(LessonConnection.CONNECTED, vm.state.value.connection)
        }

    @Test
    fun opensAtTheSavedStep() =
        runTest(dispatcher) {
            settings.update { it.copy(lessonSteps = mapOf(Skills.MIDDLE_C.id to LessonStep.TRY)) }

            val vm = viewModel()

            assertEquals(LessonStep.TRY, vm.state.value.step)
            assertNotNull(vm.state.value.tryProgress)
        }

    @Test
    fun anUnreadableStoreOpensAtWhy() =
        runTest(dispatcher) {
            settings.failing = true
            val vm = viewModel()
            assertEquals(LessonStep.WHY, vm.state.value.step)

            vm.next()
            runCurrent()
            assertEquals(LessonStep.SEE, vm.state.value.step)
        }

    @Test
    fun nextAndGoToSaveTheStepAndRecapClearsIt() =
        runTest(dispatcher) {
            val vm = viewModel()

            vm.next()
            runCurrent()
            assertEquals(LessonStep.SEE, vm.state.value.step)
            assertEquals(LessonStep.SEE, savedStep())

            vm.goTo(LessonStep.USE)
            runCurrent()
            assertEquals(LessonStep.USE, vm.state.value.step)
            assertEquals(LessonStep.USE, savedStep())

            vm.next()
            runCurrent()
            assertEquals(LessonStep.RECAP, vm.state.value.step)
            assertNull(savedStep())

            // A finished lesson reopens at the start.
            vm.next()
            runCurrent()
            assertEquals(LessonStep.RECAP, vm.state.value.step)
            assertEquals(LessonStep.WHY, viewModel().state.value.step)
        }

    @Test
    fun startingAtRecapShowsItAndKeepsNothingSaved() =
        runTest(dispatcher) {
            settings.update { it.copy(lessonSteps = mapOf(Skills.MIDDLE_C.id to LessonStep.USE)) }

            val vm = viewModel(startAt = LessonStep.RECAP)

            assertEquals(LessonStep.RECAP, vm.state.value.step)
            assertNull(savedStep())
        }

    @Test
    fun seePlaysOnThePianoWhenItIsListedAndLightsTheKeys() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.goTo(LessonStep.SEE)
            runCurrent()

            assertTrue(vm.state.value.playing)
            assertEquals(setOf(60), vm.state.value.litKeys)
            assertTrue(piano.sent.isNotEmpty())
            assertTrue(tone.played.isEmpty())

            finishDemo()
            assertFalse(vm.state.value.playing)
            assertEquals(emptySet(), vm.state.value.litKeys)
        }

    @Test
    fun seePlaysOnTheSpeakerWhenThePianoIsNotAnOutput() =
        runTest(dispatcher) {
            piano.inputOnly = true
            val vm = viewModel()
            vm.goTo(LessonStep.SEE)
            runCurrent()

            assertTrue(piano.sent.isEmpty())
            assertEquals(listOf(60), tone.played.single().map { it.note.number })
            assertTrue(vm.state.value.playing)
        }

    @Test
    fun aFailedSendMovesTheDemoToTheSpeaker() =
        runTest(dispatcher) {
            piano.failSends = true
            val vm = viewModel()
            vm.goTo(LessonStep.SEE)
            runCurrent()

            assertEquals(1, tone.played.size)
            vm.replaySee()
            runCurrent()
            // The piano is not tried again once it failed.
            assertEquals(2, tone.played.size)
        }

    @Test
    fun replayPlaysTheDemoAgain() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.goTo(LessonStep.SEE)
            finishDemo()
            vm.goTo(LessonStep.SEE)
            runCurrent()
            val first = piano.sent.size

            vm.replaySee()
            runCurrent()

            assertTrue(vm.state.value.playing)
            assertTrue(piano.sent.size > first)
            // Only See replays.
            vm.goTo(LessonStep.WHY)
            vm.replaySee()
            runCurrent()
            assertFalse(vm.state.value.playing)
        }

    @Test
    fun skipStopsThePlaybackAndMovesOn() =
        runTest(dispatcher) {
            piano.inputOnly = true
            val vm = viewModel()
            vm.goTo(LessonStep.SEE)
            runCurrent()
            assertTrue(tone.playing)

            vm.skip()
            runCurrent()

            assertFalse(tone.playing)
            assertFalse(vm.state.value.playing)
            assertEquals(emptySet(), vm.state.value.litKeys)
            assertEquals(LessonStep.TRY, vm.state.value.step)
        }

    @Test
    fun anyKeyHasNothingToPlayInSee() =
        runTest(dispatcher) {
            val vm = viewModel(Skills.HAND_SHAPE)
            vm.goTo(LessonStep.SEE)
            runCurrent()

            assertFalse(vm.state.value.playing)
            assertTrue(piano.sent.isEmpty())
            assertTrue(tone.played.isEmpty())
        }

    @Test
    fun aClickedDemoStartsAndStopsTheMetronome() =
        runTest(dispatcher) {
            val vm = viewModel(Skills.STEADY_BEAT)
            vm.goTo(LessonStep.SEE)
            runCurrent()
            assertNotNull(metronome.running.value)

            // Played again by hand, so the lesson stays in See when it ends.
            vm.replaySee()
            runCurrent()
            finishDemo()
            assertNull(metronome.running.value)
            assertFalse(vm.state.value.playing)
        }

    @Test
    fun tryNotesAdvanceTheProgress() =
        runTest(dispatcher) {
            val vm = viewModel(Skills.RH_C_POSITION)
            toTry(vm)
            assertEquals(
                0,
                vm.state.value.tryProgress
                    ?.done,
            )
            assertEquals(
                9,
                vm.state.value.tryProgress
                    ?.total,
            )

            press(60)
            press(62)

            assertEquals(
                2,
                vm.state.value.tryProgress
                    ?.done,
            )
            assertEquals(
                64,
                vm.state.value.tryProgress
                    ?.expected,
            )
            assertTrue(62 in vm.state.value.litKeys)
            assertEquals(setOf(64), vm.state.value.glowKeys)
        }

    @Test
    fun aWrongKeySetsTheFlashAndTheExpectedKey() =
        runTest(dispatcher) {
            val vm = viewModel()
            toTry(vm)

            press(62)

            assertEquals(
                62,
                vm.state.value.tryProgress
                    ?.lastWrong,
            )
            assertEquals(
                60,
                vm.state.value.tryProgress
                    ?.expected,
            )
            assertEquals(setOf(60), vm.state.value.glowKeys)
            assertEquals(
                1,
                vm.state.value.tryProgress
                    ?.wrongCount,
            )
        }

    @Test
    fun anUnlitKeyGlowsOnlyAfterAWrongKey() =
        runTest(dispatcher) {
            val vm = viewModel(Skills.BLACK_KEY_GROUPS)
            toTry(vm)
            assertEquals(emptySet(), vm.state.value.glowKeys)

            press(62)
            assertEquals(setOf(60), vm.state.value.glowKeys)
        }

    @Test
    fun finishingShowsTheTickThenMovesToUse() =
        runTest(dispatcher) {
            val vm = viewModel()
            toTry(vm)

            press(60)
            assertTrue(
                vm.state.value.tryProgress
                    ?.finished == true,
            )
            assertEquals(LessonStep.TRY, vm.state.value.step)

            advanceTimeBy(Tunables.LESSON_TRY_DONE_PAUSE_MS - 1)
            runCurrent()
            assertEquals(LessonStep.TRY, vm.state.value.step)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(LessonStep.USE, vm.state.value.step)
            assertEquals(LessonStep.USE, savedStep())
        }

    @Test
    fun skippingTheTryMovesToUse() =
        runTest(dispatcher) {
            val vm = viewModel()
            toTry(vm)

            vm.skip()
            runCurrent()

            assertEquals(LessonStep.USE, vm.state.value.step)
        }

    @Test
    fun notesOutsideTheTryDoNotCount() =
        runTest(dispatcher) {
            val vm = viewModel()
            press(60)
            assertNull(vm.state.value.tryProgress)
            assertEquals(emptySet(), vm.state.value.litKeys)
            toTry(vm)
            piano.release(MidiNote(60))
            runCurrent()
            assertEquals(
                false,
                vm.state.value.tryProgress
                    ?.finished,
            )
        }

    @Test
    fun aHoldChecksTheReleaseAndStartsTheClickWithACountIn() =
        runTest(dispatcher) {
            val vm = viewModel(Skills.QUARTER_AND_HALF)
            toTry(vm)
            assertNotNull(metronome.running.value)
            // During the count-in nothing counts.
            press(60)
            assertEquals(
                0,
                vm.state.value.tryProgress
                    ?.wrongCount,
            )
            assertEquals(
                false,
                vm.state.value.tryProgress
                    ?.finished,
            )

            // One beat to the first click and a bar of four: after that notes count.
            advanceTimeBy(5_000)
            runCurrent()
            assertNull(vm.state.value.countIn)
            piano.clock.advanceNanos(0)
            press(60)
            piano.clock.advanceNanos(2_000_000_000)
            piano.release(MidiNote(60))
            runCurrent()
            assertTrue(
                vm.state.value.tryProgress
                    ?.finished == true,
            )

            vm.close()
            assertNull(metronome.running.value)
        }

    @Test
    fun aFirstBeatPlayedJustBeforeTheEndOfTheCountInCounts() =
        runTest(dispatcher) {
            val vm = viewModel(Skills.STEADY_BEAT)
            toTry(vm)
            // At 60 BPM the count-in ends 5 s in: a press 100 ms early is the learner's first beat.
            advanceTimeBy(4_900)
            runCurrent()
            press(60)
            assertEquals(
                1,
                vm.state.value.tryProgress
                    ?.done,
            )
            vm.close()
        }

    @Test
    fun theCountInShowsThreeTwoOne() =
        runTest(dispatcher) {
            val vm = viewModel(Skills.STEADY_BEAT)
            toTry(vm)
            val seen = ArrayList<Int?>()
            repeat(6) {
                seen += vm.state.value.countIn
                advanceTimeBy(1_000)
                runCurrent()
            }
            assertEquals(listOf(null, null, 3, 2, 1, null), seen)
        }

    @Test
    fun aPassageCheckSkipsTry() =
        runTest(dispatcher) {
            val vm = viewModel(Skills.FINGER_NUMBER_TUNES)
            assertTrue(vm.state.value.tryMergedIntoUse)

            vm.goTo(LessonStep.SEE)
            runCurrent()
            vm.next()
            runCurrent()
            assertEquals(LessonStep.USE, vm.state.value.step)

            vm.goTo(LessonStep.TRY)
            runCurrent()
            assertEquals(LessonStep.USE, vm.state.value.step)
            assertEquals(
                Skills.FINGER_NUMBER_TUNES.id,
                vm.state.value.usePassage?.skills?.first {
                    it ==
                        Skills.FINGER_NUMBER_TUNES.id
                },
            )
        }

    @Test
    fun recapNamesTheNextUntickedSkill() =
        runTest(dispatcher) {
            val vm = viewModel(startAt = LessonStep.RECAP)

            // Nothing ticked: this concept counts as done, so the first other one is next.
            assertEquals(
                RecapText("Middle C", "Sit, hand shape, fingers 1–5", Skills.HAND_SHAPE.id),
                vm.state.value.recap,
            )

            tickBefore(Skills.RH_C_POSITION)
            tick(Skills.RH_C_POSITION)
            val later = viewModel(Skills.RH_C_POSITION, startAt = LessonStep.RECAP)
            assertEquals(
                Skills.STEPS_AND_SKIPS.title,
                later.state.value.recap
                    ?.nextTitle,
            )
        }

    @Test
    fun aPlacedLearnersRecapPointsPastThePlacedUnits() =
        runTest(dispatcher) {
            val unit1 = Curriculum.units[1].skills
            LearnerModel(Curriculum.graph, learnerRepository)
                .place(Curriculum.units[0].skills.toSet(), kotlinx.datetime.LocalDate(2026, 11, 15))
            val vm = viewModel(Skills.RH_C_POSITION, startAt = LessonStep.RECAP)

            assertEquals(
                unit1[1],
                vm.state.value.recap
                    ?.nextSkillId,
            )
        }

    @Test
    fun recapSaysTheCourseIsFinishedAtTheEnd() =
        runTest(dispatcher) {
            tickBefore(Skills.SHARPS_AND_FLATS)

            val vm = viewModel(Skills.SHARPS_AND_FLATS, startAt = LessonStep.RECAP)

            assertNull(
                vm.state.value.recap
                    ?.nextTitle,
            )
            assertNull(
                vm.state.value.recap
                    ?.nextSkillId,
            )
            assertEquals(
                Skills.SHARPS_AND_FLATS.title,
                vm.state.value.recap
                    ?.learned,
            )
        }

    @Test
    fun recapFallsBackToMapOrderWhenTheTicksCantBeRead() =
        runTest(dispatcher) {
            attempts.failing = true

            val vm = viewModel(Skills.STEADY_BEAT, startAt = LessonStep.RECAP)

            assertEquals(
                Skills.QUARTER_AND_HALF.title,
                vm.state.value.recap
                    ?.nextTitle,
            )
        }

    @Test
    fun aDroppedPianoIsShownAndTheLessonStaysOpen() =
        runTest(dispatcher) {
            val vm = viewModel()
            piano.unplug()
            runCurrent()

            assertEquals(LessonConnection.DISCONNECTED, vm.state.value.connection)
            vm.next()
            runCurrent()
            assertEquals(LessonStep.SEE, vm.state.value.step)
        }

    @Test
    fun theUnavailablePianoAtOpeningIsShown() =
        runTest(dispatcher) {
            piano.unplug()
            val vm = viewModel()

            assertEquals(LessonConnection.DISCONNECTED, vm.state.value.connection)
        }

    @Test
    fun closeStopsTheSoundAndClosesThePianoOnce() =
        runTest(dispatcher) {
            piano.inputOnly = true
            val vm = viewModel()
            assertEquals(1, input.open)
            vm.goTo(LessonStep.SEE)
            runCurrent()
            assertTrue(tone.playing)

            vm.close()

            assertEquals(0, input.open)
            assertFalse(tone.playing)
            assertEquals(LessonConnection.CONNECTED, vm.state.value.connection)
            // Later taps start nothing.
            vm.next()
            runCurrent()
            assertEquals(LessonStep.SEE, vm.state.value.step)
        }

    private suspend fun tick(skill: Skill) {
        val passage = Curriculum.passages.first { skill.id in it.skills }
        attempts.save(
            Attempt(
                id = "a-${passage.id}",
                passageId = passage.id,
                deviceId = "usb-1",
                startedAt = Instant.parse("2026-11-15T09:00:00Z"),
                durationNanos = 1,
                stepCount = 1,
                wrongNotes = 0,
                incompleteChords = 0,
                accuracy = 1.0,
                band = Band.CLEAN,
                events = emptyList(),
                tempoBpm = null,
                playedNotes = emptyList(),
            ),
        )
    }

    private suspend fun tickBefore(skill: Skill) {
        CourseProgress
            .skills(Curriculum)
            .takeWhile { it != skill }
            .forEach { tick(it) }
    }
}
