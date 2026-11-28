package pianoanchor.testing

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.BarRange
import pianoanchor.core.model.ChordIncomplete
import pianoanchor.core.model.HintLevel
import pianoanchor.core.model.MatchEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.PassageComplete
import pianoanchor.core.model.PlayedNote
import pianoanchor.core.model.RunMode
import pianoanchor.core.model.StepCorrect
import pianoanchor.core.model.WrongNote
import pianoanchor.core.model.summary
import pianoanchor.core.ports.AttemptRepositoryPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

private const val LATENCY_OFFSET = 38_000_000L

/** Behaviour every [AttemptRepositoryPort] adapter must satisfy. Subclass it once per adapter. */
abstract class AttemptRepositoryPortContractTest {
    protected abstract fun createSubject(): AttemptRepositoryPort

    private val events =
        listOf(
            StepCorrect(0, 0),
            WrongNote(1_000, 1, MidiNote(63)),
            ChordIncomplete(2_000, 1, setOf(MidiNote(64), MidiNote(67))),
            StepCorrect(3_000, 1),
            PassageComplete(3_000, 1),
        )

    private fun attempt(
        id: String,
        startedAtSeconds: Long,
        passageId: String = "c-d-e",
        tempoBpm: Int? = null,
        band: Band = Band.KEEP_PRACTISING,
        playedNotes: List<PlayedNote> = emptyList(),
        latencyOffsetNanos: Long? = null,
        hintLevel: HintLevel = HintLevel.FULL,
        hintShown: Boolean = true,
        mode: RunMode = RunMode.PRACTICE,
        spot: BarRange? = null,
        durationNanos: Long = 3_000,
        events: List<MatchEvent> = this.events,
    ) = Attempt(
        id = id,
        passageId = passageId,
        deviceId = "usb-1",
        startedAt = Instant.fromEpochSeconds(startedAtSeconds),
        durationNanos = durationNanos,
        stepCount = 2,
        wrongNotes = 1,
        incompleteChords = 1,
        accuracy = 0.5,
        band = band,
        events = events,
        tempoBpm = tempoBpm,
        playedNotes = playedNotes,
        latencyOffsetNanos = latencyOffsetNanos,
        hintLevel = hintLevel,
        hintShown = hintShown,
        mode = mode,
        spot = spot,
    )

    private suspend fun AttemptRepositoryPort.countBefore(
        beforeSeconds: Long,
        passageId: String = "c-d-e",
    ) = countStartedBefore(passageId, Instant.fromEpochSeconds(beforeSeconds))

    @Test
    fun unknownIdIsNull() =
        runTest {
            assertNull(createSubject().get("a-1"))
        }

    @Test
    fun saveThenGetReturnsTheAttemptWithItsEvents() =
        runTest {
            val repository = createSubject()
            val attempt = attempt("a-1", startedAtSeconds = 1_000)
            repository.save(attempt)
            assertEquals(attempt, repository.get("a-1"))
        }

    @Test
    fun savingAnIdTwiceThrowsAndKeepsTheFirst() =
        runTest {
            val repository = createSubject()
            val first = attempt("a-1", startedAtSeconds = 1_000)
            repository.save(first)
            assertFailsWith<IllegalArgumentException> { repository.save(attempt("a-1", startedAtSeconds = 2_000)) }
            assertEquals(first, repository.get("a-1"))
        }

    @Test
    fun recentIsNewestFirstLimitedAndFilteredByPassage() =
        runTest {
            val repository = createSubject()
            val oldest = attempt("a-1", startedAtSeconds = 1_000)
            val newest = attempt("a-3", startedAtSeconds = 3_000)
            val middle = attempt("a-2", startedAtSeconds = 2_000)
            val otherPassage = attempt("b-1", startedAtSeconds = 4_000, passageId = "other")
            listOf(oldest, newest, middle, otherPassage).forEach { repository.save(it) }
            assertEquals(listOf(newest, middle, oldest), repository.recent("c-d-e", limit = 10).first())
            assertEquals(listOf(newest, middle), repository.recent("c-d-e", limit = 2).first())
            assertEquals(emptyList(), repository.recent("unknown", limit = 10).first())
        }

    @Test
    fun recentEmitsAgainAfterASave() =
        runTest {
            val repository = createSubject()
            val older = attempt("a-1", startedAtSeconds = 1_000)
            val newer = attempt("a-2", startedAtSeconds = 2_000)
            repository.save(older)
            val bothSeen =
                async(start = CoroutineStart.UNDISPATCHED) {
                    repository.recent("c-d-e", limit = 10).first { it.size == 2 }
                }
            repository.save(newer)
            assertEquals(listOf(newer, older), bothSeen.await())
        }

    @Test
    fun countStartedBeforeCountsOnlyOlderAttemptsOfThePassage() =
        runTest {
            val repository = createSubject()
            listOf(
                attempt("a-1", startedAtSeconds = 1_000),
                attempt("a-2", startedAtSeconds = 2_000),
                attempt("a-3", startedAtSeconds = 3_000),
                attempt("b-1", startedAtSeconds = 1_500, passageId = "other"),
            ).forEach { repository.save(it) }
            assertEquals(2, repository.countBefore(beforeSeconds = 3_000))
            assertEquals(1, repository.countBefore(beforeSeconds = 2_000))
            assertEquals(0, repository.countBefore(beforeSeconds = 1_000))
            assertEquals(0, repository.countBefore(beforeSeconds = 9_000, passageId = "unknown"))
        }

    @Suppress("MagicNumber") // Epoch seconds of the scenario.
    @Test
    fun countStartedBeforeIgnoresSpotAttempts() =
        runTest {
            val repository = createSubject()
            repository.save(attempt("a-1", startedAtSeconds = 1_000))
            repository.save(attempt("a-2", startedAtSeconds = 2_000, spot = BarRange(1, 2)))
            assertEquals(1, repository.countBefore(beforeSeconds = 9_000))
        }

    @Suppress("MagicNumber") // Epoch seconds of the scenario.
    @Test
    fun recentListsOnlyFullRuns() =
        runTest {
            val repository = createSubject()
            repository.save(attempt("a-1", startedAtSeconds = 1_000))
            repository.save(attempt("a-2", startedAtSeconds = 2_000, spot = BarRange(1, 2)))
            repository.save(attempt("a-3", startedAtSeconds = 3_000, spot = BarRange(1, 2)))
            assertEquals(listOf("a-1"), repository.recent("c-d-e", limit = 2).first().map { it.id })
        }

    @Suppress("MagicNumber") // Epoch seconds of the scenario.
    @Test
    fun lastPlayedCountsSpotAttempts() =
        runTest {
            val repository = createSubject()
            repository.save(attempt("a-1", startedAtSeconds = 1_000))
            repository.save(attempt("a-2", startedAtSeconds = 2_000, spot = BarRange(1, 2)))
            assertEquals(Instant.fromEpochSeconds(2_000), repository.lastPlayed("c-d-e"))
        }

    @Suppress("MagicNumber") // Epoch seconds and calendar dates of the scenario.
    @Test
    fun practiceDaysCountSpotAttempts() =
        runTest {
            val repository = createSubject()
            repository.save(attempt("a-1", startedAtSeconds = 0, spot = BarRange(1, 2)))
            assertEquals(
                setOf(LocalDate(1970, 1, 1)),
                repository.practiceDays(LocalDate(1970, 1, 1), TimeZone.UTC),
            )
        }

    @Test
    fun countStartedBeforeIsZeroWhenNothingIsStored() =
        runTest {
            assertEquals(0, createSubject().countBefore(beforeSeconds = 9_000))
        }

    @Test
    fun recentRejectsANonPositiveLimit() =
        runTest {
            assertFailsWith<IllegalArgumentException> { createSubject().recent("c-d-e", limit = 0).first() }
        }

    @Test
    fun saveAndGetRoundTripTempoBpmAndPlayedNotes() =
        runTest {
            val repository = createSubject()
            val notes =
                listOf(
                    PlayedNote(MidiNote(60), velocity = 64, downNanos = 0, upNanos = 400, soundingEndNanos = 900),
                    PlayedNote(MidiNote(64), velocity = 90, downNanos = 500, upNanos = 500, soundingEndNanos = 500),
                )
            val timed = attempt("a-1", startedAtSeconds = 1_000, tempoBpm = 72, playedNotes = notes)
            val wait = attempt("a-2", startedAtSeconds = 2_000, playedNotes = notes.take(1))
            repository.save(timed)
            repository.save(wait)
            assertEquals(timed, repository.get("a-1"))
            assertEquals(wait, repository.get("a-2"))
            assertEquals(listOf(wait, timed), repository.recent("c-d-e", limit = 10).first())
        }

    @Test
    fun saveAndGetRoundTripTheLatencyOffset() =
        runTest {
            val repository = createSubject()
            val calibrated =
                attempt("a-1", startedAtSeconds = 1_000, tempoBpm = 72, latencyOffsetNanos = -LATENCY_OFFSET)
            val uncalibrated = attempt("a-2", startedAtSeconds = 2_000, tempoBpm = 72)
            repository.save(calibrated)
            repository.save(uncalibrated)
            assertEquals(calibrated, repository.get("a-1"))
            assertEquals(uncalibrated, repository.get("a-2"))
        }

    @Test
    fun saveAndGetRoundTripTheHintLevelAndFlag() =
        runTest {
            val repository = createSubject()
            val unaided = attempt("a-1", startedAtSeconds = 1_000, hintLevel = HintLevel.OFF, hintShown = false)
            val delayed = attempt("a-2", startedAtSeconds = 2_000, hintLevel = HintLevel.DELAYED, hintShown = true)
            repository.save(unaided)
            repository.save(delayed)
            assertEquals(unaided, repository.get("a-1"))
            assertEquals(delayed, repository.get("a-2"))
        }

    @Test
    fun saveAndGetRoundTripTheRunMode() =
        runTest {
            val repository = createSubject()
            val perform = attempt("a-1", startedAtSeconds = 1_000, mode = RunMode.PERFORM, hintShown = false)
            val practice = attempt("a-2", startedAtSeconds = 2_000)
            repository.save(perform)
            repository.save(practice)
            assertEquals(perform, repository.get("a-1"))
            assertEquals(practice, repository.get("a-2"))
        }

    @Test
    fun saveAndGetRoundTripTheSpotRange() =
        runTest {
            val repository = createSubject()
            val spot = attempt("a-1", startedAtSeconds = 1_000, spot = BarRange(5, 6))
            val full = attempt("a-2", startedAtSeconds = 2_000)
            repository.save(spot)
            repository.save(full)
            assertEquals(spot, repository.get("a-1"))
            assertNull(assertNotNull(repository.get("a-2")).spot)
        }

    @Test
    fun spotAttemptsAreNotCleanRuns() =
        runTest {
            val repository = createSubject()
            val spot = BarRange(1, 2)
            repository.save(
                attempt("a-1", startedAtSeconds = 1_000, tempoBpm = TEMPO_A, band = Band.CLEAN, spot = spot),
            )
            repository.save(attempt("a-2", startedAtSeconds = 2_000, band = Band.CLEAN, spot = spot))
            assertEquals(emptySet(), repository.cleanTempos("c-d-e"))
            assertEquals(false, repository.hasCleanWaitRun("c-d-e"))
        }

    @Test
    fun cleanTemposListsOnlyCleanTimedTempos() =
        runTest {
            val repository = createSubject()
            listOf(
                Triple(TEMPO_A, Band.CLEAN, "c-d-e"),
                Triple(TEMPO_A, Band.CLEAN, "c-d-e"),
                Triple(TEMPO_B, Band.GOOD, "c-d-e"),
                Triple(TEMPO_C, Band.KEEP_PRACTISING, "c-d-e"),
                Triple(null, Band.CLEAN, "c-d-e"),
                Triple(TEMPO_D, Band.CLEAN, "other"),
                Triple(TEMPO_E, Band.CLEAN, "c-d-e"),
            ).forEachIndexed { i, (tempo, band, passageId) ->
                repository.save(attempt("a-$i", (i + 1) * SECONDS_APART, passageId, tempo, band))
            }
            assertEquals(setOf(TEMPO_A, TEMPO_E), repository.cleanTempos("c-d-e"))
            assertEquals(setOf(TEMPO_D), repository.cleanTempos("other"))
            assertEquals(emptySet(), repository.cleanTempos("unknown"))
        }

    @Test
    fun hasCleanWaitRunIsTrueOnlyForACleanAttemptWithoutATempo() =
        runTest {
            val repository = createSubject()
            listOf(
                attempt("a-1", startedAtSeconds = 1_000, band = Band.GOOD),
                attempt("a-2", startedAtSeconds = 2_000, tempoBpm = TEMPO_A, band = Band.CLEAN),
                attempt("a-3", startedAtSeconds = 3_000, passageId = "other", band = Band.CLEAN),
            ).forEach { repository.save(it) }
            assertEquals(false, repository.hasCleanWaitRun("c-d-e"))
            assertEquals(true, repository.hasCleanWaitRun("other"))
            repository.save(attempt("a-4", startedAtSeconds = 4_000, band = Band.CLEAN))
            assertEquals(true, repository.hasCleanWaitRun("c-d-e"))
            assertEquals(false, repository.hasCleanWaitRun("unknown"))
        }

    @Suppress("MagicNumber") // Epoch seconds of the scenario.
    @Test
    fun firstCleanUnaidedWaitRunIsTheEarliestCleanWaitRunWithoutAHint() =
        runTest {
            val repository = createSubject()
            assertNull(repository.firstCleanUnaidedWaitRun("c-d-e"))
            listOf(
                attempt("a-1", startedAtSeconds = 1_000, band = Band.CLEAN, hintShown = true),
                attempt("a-2", startedAtSeconds = 2_000, band = Band.GOOD, hintShown = false),
                attempt("a-3", startedAtSeconds = 3_000, tempoBpm = TEMPO_A, band = Band.CLEAN, hintShown = false),
                attempt("a-4", startedAtSeconds = 4_000, passageId = "other", band = Band.CLEAN, hintShown = false),
                attempt("a-5", startedAtSeconds = 5_000, band = Band.CLEAN, hintShown = false, spot = BarRange(1, 2)),
            ).forEach { repository.save(it) }
            assertNull(repository.firstCleanUnaidedWaitRun("c-d-e"))
            assertEquals(Instant.fromEpochSeconds(4_000), repository.firstCleanUnaidedWaitRun("other"))
            repository.save(attempt("a-7", startedAtSeconds = 7_000, band = Band.CLEAN, hintShown = false))
            repository.save(attempt("a-6", startedAtSeconds = 6_000, band = Band.CLEAN, hintShown = false))
            repository.save(attempt("a-8", startedAtSeconds = 8_000, band = Band.CLEAN, hintShown = false))
            assertEquals(Instant.fromEpochSeconds(6_000), repository.firstCleanUnaidedWaitRun("c-d-e"))
        }

    @Suppress("MagicNumber") // Epoch seconds and calendar dates of the scenario.
    @Test
    fun lastPlayedIsTheLatestStartOfThePassage() =
        runTest {
            val repository = createSubject()
            assertNull(repository.lastPlayed("c-d-e"))
            listOf(
                attempt("a-1", startedAtSeconds = 2_000),
                attempt("a-2", startedAtSeconds = 3_000),
                attempt("a-3", startedAtSeconds = 1_000),
                attempt("b-1", startedAtSeconds = 9_000, passageId = "other"),
            ).forEach { repository.save(it) }
            assertEquals(Instant.fromEpochSeconds(3_000), repository.lastPlayed("c-d-e"))
            assertEquals(Instant.fromEpochSeconds(9_000), repository.lastPlayed("other"))
            assertNull(repository.lastPlayed("unknown"))
        }

    @Suppress("MagicNumber") // Epoch seconds and calendar dates of the scenario.
    @Test
    fun practiceDaysListsTheLocalDaysFromTheGivenOneOn() =
        runTest {
            val repository = createSubject()
            val plusTwo = TimeZone.of("Etc/GMT-2")
            // 2026-11-13 23:30 UTC is already the 14th at UTC+2.
            listOf(
                "2026-11-12T10:00:00Z",
                "2026-11-13T23:30:00Z",
                "2026-11-13T23:45:00Z",
                "2026-11-15T08:00:00Z",
            ).forEachIndexed { i, at ->
                repository.save(
                    attempt("a-$i", Instant.parse(at).epochSeconds, passageId = if (i % 2 == 0) "c-d-e" else "other"),
                )
            }
            assertEquals(
                setOf(LocalDate(2026, 11, 13), LocalDate(2026, 11, 15)),
                repository.practiceDays(LocalDate(2026, 11, 13), TimeZone.UTC),
            )
            assertEquals(
                setOf(LocalDate(2026, 11, 14), LocalDate(2026, 11, 15)),
                repository.practiceDays(LocalDate(2026, 11, 13), plusTwo),
            )
            assertEquals(emptySet(), repository.practiceDays(LocalDate(2026, 11, 16), TimeZone.UTC))
        }

    // A run that stopped short: its events never end the passage.
    private val unfinished = events.dropLast(1)

    @Test
    fun firstFullRunIsTheOldestCompleteFullRun() =
        runTest {
            val repository = createSubject()
            repository.save(attempt("a-early-spot", startedAtSeconds = 100, spot = BarRange(1, 2)))
            repository.save(attempt("a-early-unfinished", startedAtSeconds = 200, events = unfinished))
            repository.save(attempt("a-3", startedAtSeconds = 3_000))
            repository.save(attempt("a-2", startedAtSeconds = 2_000))
            repository.save(attempt("a-other", startedAtSeconds = 50, passageId = "other"))
            repository.save(attempt("a-4", startedAtSeconds = 4_000))
            assertEquals(attempt("a-2", startedAtSeconds = 2_000), repository.firstFullRun("c-d-e"))
        }

    @Test
    fun latestFullRunIsTheNewestCompleteFullRun() =
        runTest {
            val repository = createSubject()
            repository.save(attempt("a-1", startedAtSeconds = 1_000))
            repository.save(attempt("a-2", startedAtSeconds = 2_000))
            repository.save(attempt("a-late-spot", startedAtSeconds = 9_000, spot = BarRange(1, 2)))
            repository.save(attempt("a-late-unfinished", startedAtSeconds = 8_000, events = unfinished))
            repository.save(attempt("a-other", startedAtSeconds = 9_500, passageId = "other"))
            assertEquals(attempt("a-2", startedAtSeconds = 2_000), repository.latestFullRun("c-d-e"))
        }

    @Test
    fun fullRunsBreakStartTiesById() =
        runTest {
            val repository = createSubject()
            repository.save(attempt("a-b", startedAtSeconds = 1_000))
            repository.save(attempt("a-a", startedAtSeconds = 1_000))
            assertEquals("a-a", assertNotNull(repository.firstFullRun("c-d-e")).id)
            assertEquals("a-b", assertNotNull(repository.latestFullRun("c-d-e")).id)
        }

    @Test
    fun fullRunsAreNullWhenThereAreNoCompleteOnes() =
        runTest {
            val repository = createSubject()
            assertNull(repository.firstFullRun("c-d-e"))
            assertNull(repository.latestFullRun("c-d-e"))
            repository.save(attempt("a-spot", startedAtSeconds = 1_000, spot = BarRange(1, 2)))
            repository.save(attempt("a-unfinished", startedAtSeconds = 2_000, events = unfinished))
            assertNull(repository.firstFullRun("c-d-e"))
            assertNull(repository.latestFullRun("c-d-e"))
        }

    @Test
    fun theFirstFullRunKeepsItsEventsAndNotes() =
        runTest {
            val repository = createSubject()
            val notes =
                listOf(PlayedNote(MidiNote(60), velocity = 64, downNanos = 0, upNanos = 400, soundingEndNanos = 900))
            val saved = attempt("a-1", startedAtSeconds = 1_000, playedNotes = notes)
            repository.save(saved)
            assertEquals(saved, repository.firstFullRun("c-d-e"))
            assertEquals(saved, repository.latestFullRun("c-d-e"))
        }

    @Suppress("MagicNumber") // Dates, durations and timestamps of the scenario.
    @Test
    fun minutesByDaySumsPerLocalDayAndRoundsHalfUp() =
        runTest {
            val repository = createSubject()
            val plusTwo = TimeZone.of("Etc/GMT-2")
            val minute = 60_000_000_000L
            // 2026-11-13 23:30 UTC is already the 14th at UTC+2. 90 s + 90 s = 3 min; 30 s rounds up to 1 min.
            repository.save(
                attempt(
                    "a-0",
                    Instant.parse("2026-11-12T10:00:00Z").epochSeconds,
                    durationNanos =
                        minute * 3 / 2,
                ),
            )
            repository.save(
                attempt(
                    "a-1",
                    Instant.parse("2026-11-12T11:00:00Z").epochSeconds,
                    durationNanos =
                        minute * 3 / 2,
                ),
            )
            repository.save(
                attempt(
                    "a-2",
                    Instant.parse("2026-11-13T23:30:00Z").epochSeconds,
                    passageId = "other",
                    spot = BarRange(1, 2),
                    durationNanos = minute / 2,
                ),
            )
            repository.save(
                attempt(
                    "a-3",
                    Instant.parse("2026-11-15T08:00:00Z").epochSeconds,
                    durationNanos =
                        minute / 2 - 1,
                ),
            )
            assertEquals(
                mapOf(LocalDate(2026, 11, 12) to 3, LocalDate(2026, 11, 13) to 1, LocalDate(2026, 11, 15) to 0),
                repository.minutesByDay(LocalDate(2026, 11, 1), TimeZone.UTC),
            )
            assertEquals(
                mapOf(LocalDate(2026, 11, 14) to 1, LocalDate(2026, 11, 15) to 0),
                repository.minutesByDay(LocalDate(2026, 11, 13), plusTwo),
            )
            assertEquals(emptyMap(), repository.minutesByDay(LocalDate(2026, 11, 16), TimeZone.UTC))
        }

    @Test
    fun summariesWithPassagePrefixReturnsMatchesOldestFirst() =
        runTest {
            val repository = createSubject()
            repository.save(attempt("a-1", startedAtSeconds = 3_000, passageId = "sight:1:20"))
            repository.save(attempt("a-2", startedAtSeconds = 1_000, passageId = "sight:2:10"))
            repository.save(attempt("a-3", startedAtSeconds = 2_000, passageId = "sight:1:20"))
            assertEquals(listOf("a-2", "a-3", "a-1"), repository.summariesWithPassagePrefix("sight:").map { it.id })
            val saved = attempt("a-2", startedAtSeconds = 1_000, passageId = "sight:2:10")
            assertEquals(saved.summary, repository.summariesWithPassagePrefix("sight:").first())
        }

    @Test
    fun summariesWithPassagePrefixIgnoresOthers() =
        runTest {
            val repository = createSubject()
            repository.save(attempt("a-1", startedAtSeconds = 1_000, passageId = "sight:1:20"))
            repository.save(attempt("a-2", startedAtSeconds = 2_000, passageId = "c-d-e"))
            repository.save(attempt("a-3", startedAtSeconds = 3_000, passageId = "sigh%"))
            repository.save(attempt("a-4", startedAtSeconds = 4_000, passageId = "x-sight:1"))
            assertEquals(listOf("a-1"), repository.summariesWithPassagePrefix("sight:").map { it.id })
            assertEquals(emptyList(), repository.summariesWithPassagePrefix("nothing:"))
            // A wildcard is plain text, not a pattern.
            assertEquals(emptyList(), repository.summariesWithPassagePrefix("%"))
        }

    @Test
    fun summariesWithPassagePrefixIncludesIdsJustAboveThePrefix() =
        runTest {
            val repository = createSubject()
            repository.save(attempt("a-1", startedAtSeconds = 1_000, passageId = "sight:"))
            repository.save(attempt("a-2", startedAtSeconds = 2_000, passageId = "sight:\uFFFF"))
            repository.save(attempt("a-3", startedAtSeconds = 3_000, passageId = "sight;"))
            repository.save(attempt("a-4", startedAtSeconds = 4_000, passageId = "sigh"))
            assertEquals(listOf("a-1", "a-2"), repository.summariesWithPassagePrefix("sight:").map { it.id })
        }

    @Test
    fun summariesWithPassagePrefixBlankPrefixThrows() =
        runTest {
            val repository = createSubject()
            assertFailsWith<IllegalArgumentException> { repository.summariesWithPassagePrefix("") }
            assertFailsWith<IllegalArgumentException> { repository.summariesWithPassagePrefix("  ") }
        }

    @Test
    fun summariesWithPassagePrefixBreaksStartTiesById() =
        runTest {
            val repository = createSubject()
            repository.save(attempt("a-2", startedAtSeconds = 1_000, passageId = "sight:1:20"))
            repository.save(attempt("a-1", startedAtSeconds = 1_000, passageId = "sight:1:21"))
            assertEquals(listOf("a-1", "a-2"), repository.summariesWithPassagePrefix("sight:").map { it.id })
        }
}

private const val TEMPO_A = 48
private const val TEMPO_B = 56
private const val TEMPO_C = 68
private const val TEMPO_D = 80
private const val TEMPO_E = 64
private const val SECONDS_APART = 1_000L
