package pianoanchor.testing

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.ChordIncomplete
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.PassageComplete
import pianoanchor.core.model.PlayedNote
import pianoanchor.core.model.StepCorrect
import pianoanchor.core.model.WrongNote
import pianoanchor.core.ports.AttemptRepositoryPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant

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
    ) = Attempt(
        id = id,
        passageId = passageId,
        deviceId = "usb-1",
        startedAt = Instant.fromEpochSeconds(startedAtSeconds),
        durationNanos = 3_000,
        stepCount = 2,
        wrongNotes = 1,
        incompleteChords = 1,
        accuracy = 0.5,
        band = band,
        events = events,
        tempoBpm = tempoBpm,
        playedNotes = playedNotes,
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
}

private const val TEMPO_A = 48
private const val TEMPO_B = 56
private const val TEMPO_C = 68
private const val TEMPO_D = 80
private const val TEMPO_E = 64
private const val SECONDS_APART = 1_000L
