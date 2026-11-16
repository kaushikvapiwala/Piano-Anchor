package pianoanchor.engine.session

import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.testing.FakeAttemptRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class PassageHistoriesTest {
    private val attempts = FakeAttemptRepository()
    private val passages = Curriculum.units.flatMap { it.passages }
    private val passage = passages.first { it.piece }
    private val other = passages.first { it.id != passage.id }
    private val startedAt = Instant.parse("2026-11-15T09:00:00Z")

    private suspend fun playClean(
        id: String,
        rung: TempoRung?,
    ) = attempts.save(
        Attempt(
            id = id,
            passageId = passage.id,
            deviceId = "usb-1",
            startedAt = startedAt,
            durationNanos = 1,
            stepCount = 1,
            wrongNotes = 0,
            incompleteChords = 0,
            accuracy = 1.0,
            band = Band.CLEAN,
            events = emptyList(),
            tempoBpm = rung?.bpm(passage),
            playedNotes = emptyList(),
        ),
    )

    @Test
    fun noPassagesGiveAnEmptyMap() =
        runTest {
            assertEquals(emptyMap(), attempts.passageHistories(emptyList()))
        }

    @Test
    fun anUnplayedPassageHasAnEmptyHistory() =
        runTest {
            assertEquals(
                mapOf(passage.id to PassageHistory(null, emptySet(), cleanWait = false)),
                attempts.passageHistories(listOf(passage)),
            )
        }

    @Test
    fun aPlayedPassageCarriesItsCleanRunsAndLastPlayed() =
        runTest {
            playClean("a", TempoRung.P60)
            playClean("b", null)

            val histories = attempts.passageHistories(listOf(passage, other))

            assertEquals(
                PassageHistory(startedAt, setOf(checkNotNull(TempoRung.P60.bpm(passage))), cleanWait = true),
                histories[passage.id],
            )
            assertEquals(PassageHistory(null, emptySet(), cleanWait = false), histories[other.id])
        }

    @Test
    fun aFailingStoreThrows() =
        runTest {
            attempts.failing = true
            assertFailsWith<StorageUnavailableException> { attempts.passageHistories(listOf(passage)) }
        }
}
