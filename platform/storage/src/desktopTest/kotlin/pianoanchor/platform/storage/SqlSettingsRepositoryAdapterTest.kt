package pianoanchor.platform.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import pianoanchor.core.model.DailyBudget
import pianoanchor.core.model.LessonStep
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.NoteNames
import pianoanchor.core.model.Settings
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.model.Strictness
import pianoanchor.core.ports.SettingsRepositoryPort
import pianoanchor.testing.SettingsRepositoryPortContractTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SqlSettingsRepositoryAdapterTest : SettingsRepositoryPortContractTest() {
    private val drivers = InMemoryDrivers()

    override fun createSubject(): SettingsRepositoryPort =
        SqlSettingsRepositoryAdapter(drivers.open(), Dispatchers.Unconfined)

    @AfterTest
    fun closeDrivers() = drivers.close()

    @Test
    fun unreadableStoredValueFallsBackToTheDefault() =
        runTest {
            val driver = drivers.open()
            val repository = SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined)
            driver.exec("INSERT INTO setting VALUES ('daily_budget', 'MIN_99'), ('weekly_goal_days', '9')")
            driver.exec(
                "INSERT INTO setting VALUES ('click_on', 'maybe'), ('update_check_on', 'false'), ('onboarded', 'yes')",
            )
            assertEquals(Settings(updateCheckOn = false), repository.settings.first())

            repository.update { it.copy(dailyBudget = DailyBudget.MIN_15) }

            assertEquals(Settings(DailyBudget.MIN_15, updateCheckOn = false), repository.settings.first())
            assertEquals(17, driver.count("setting"))
        }

    @Test
    fun unreadableUpdateCheckStateReadsAsNotSet() =
        runTest {
            val driver = drivers.open()
            driver.exec(
                "INSERT INTO setting VALUES ('last_update_check', 'yesterday'), ('latest_release', 'v1 a b'), " +
                    "('dismissed_update', '')",
            )
            assertEquals(Settings(), SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined).settings.first())
            for (unreadable in listOf("v1", "v1 ", " https://x")) {
                driver.exec("UPDATE setting SET text_value = '$unreadable' WHERE key = 'latest_release'")
                assertEquals(Settings(), SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined).settings.first())
            }
        }

    @Test
    fun anUnknownStoredStrictnessFallsBackToStandard() =
        runTest {
            val driver = drivers.open()
            driver.exec("INSERT INTO setting VALUES ('strictness', 'RELAXED')")

            val stored = SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined).settings.first()

            assertEquals(Strictness.STANDARD, stored.strictness)
        }

    @Test
    fun theLastPianoRoundTripsAndCanBeForgotten() =
        runTest {
            val driver = drivers.open()
            val repository = SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined)
            assertNull(repository.settings.first().lastPiano)
            val piano = MidiDevice("ble:AA:BB:CC:DD:EE:01", "Casio CDP S160")

            repository.update { it.copy(lastPiano = piano) }
            assertEquals(piano, SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined).settings.first().lastPiano)

            repository.update { it.copy(lastPiano = null) }
            assertNull(repository.settings.first().lastPiano)
        }

    @Test
    fun anUnreadableLastPianoReadsAsNone() =
        runTest {
            val driver = drivers.open()
            for (unreadable in listOf("ble:AA", "\nName", "ble:AA\n")) {
                driver.exec("DELETE FROM setting WHERE key = 'last_piano'")
                driver.exec("INSERT INTO setting VALUES ('last_piano', '$unreadable')")
                assertNull(SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined).settings.first().lastPiano)
            }
        }

    @Test
    fun theSavedLessonStepsRoundTripAndAnEntryCanBeRemoved() =
        runTest {
            val driver = drivers.open()
            val repository = SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined)
            assertEquals(emptyMap(), repository.settings.first().lessonSteps)
            val steps = mapOf("middle-c" to LessonStep.TRY, "hand-shape" to LessonStep.SEE)

            repository.update { it.copy(lessonSteps = steps) }
            assertEquals(
                steps,
                SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined).settings.first().lessonSteps,
            )

            repository.update { it.copy(lessonSteps = it.lessonSteps - "middle-c") }
            assertEquals(mapOf("hand-shape" to LessonStep.SEE), repository.settings.first().lessonSteps)
        }

    @Test
    fun pedalControlRoundTripsAndOldSettingsReadAsOn() =
        runTest {
            val driver = drivers.open()
            val repository = SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined)
            assertEquals(true, repository.settings.first().pedalControlOn)

            repository.update { it.copy(pedalControlOn = false) }

            assertEquals(
                false,
                SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined).settings.first().pedalControlOn,
            )
        }

    @Test
    fun noteNamesRoundTripAndOldSettingsReadAsAuto() =
        runTest {
            val driver = drivers.open()
            val repository = SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined)
            assertEquals(NoteNames.AUTO, repository.settings.first().noteNames)

            repository.update { it.copy(noteNames = NoteNames.OFF) }

            assertEquals(
                NoteNames.OFF,
                SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined).settings.first().noteNames,
            )
            driver.exec("UPDATE setting SET text_value = 'SHOUTING' WHERE key = 'note_names'")
            assertEquals(
                NoteNames.AUTO,
                SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined).settings.first().noteNames,
            )
        }

    @Test
    fun pausedWeeksRoundTripAndOldSettingsReadAsEmpty() =
        runTest {
            val driver = drivers.open()
            val repository = SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined)
            assertEquals(emptySet(), repository.settings.first().pausedWeeks)
            val weeks = setOf(LocalDate(2026, 11, 16), LocalDate(2026, 10, 19))

            repository.update { it.copy(pausedWeeks = weeks) }

            assertEquals(
                weeks,
                SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined).settings.first().pausedWeeks,
            )
            driver.exec("UPDATE setting SET text_value = '2026-11-16,soon,' WHERE key = 'paused_weeks'")
            assertEquals(
                setOf(LocalDate(2026, 11, 16)),
                SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined).settings.first().pausedWeeks,
            )
        }

    @Test
    fun aStoreWrittenWithoutTheSessionProgressLoadsTheDefaults() =
        runTest {
            val driver = drivers.open()
            driver.exec("INSERT INTO setting VALUES ('click_on', 'false')")

            val stored = SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined).settings.first()

            assertEquals(Settings(clickOn = false), stored)
        }

    @Test
    fun unreadableSessionProgressFallsBackToTheDefaults() =
        runTest {
            val driver = drivers.open()
            driver.exec(
                "INSERT INTO setting VALUES ('session_day', 'today'), ('session_finished', '\n\nlesson:a\n'), " +
                    "('session_completed', 'maybe')",
            )

            val stored = SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined).settings.first()

            assertEquals(Settings(sessionFinished = setOf("lesson:a")), stored)
        }

    @Test
    fun unreadableLessonStepsAreLeftOut() =
        runTest {
            val driver = drivers.open()
            driver.exec("INSERT INTO setting VALUES ('lesson_steps', 'middle-c=TRY\nbroken\nhand-shape=NOPE\n=SEE')")

            val stored = SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined).settings.first()

            assertEquals(mapOf("middle-c" to LessonStep.TRY), stored.lessonSteps)
        }

    @Test
    fun aNonNumericWeeklyGoalFallsBackToTheDefault() =
        runTest {
            val driver = drivers.open()
            driver.exec("INSERT INTO setting VALUES ('weekly_goal_days', 'many'), ('daily_budget', 'MIN_30')")

            val stored = SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined).settings.first()

            assertEquals(Settings(DailyBudget.MIN_30), stored)
        }

    @Test
    fun readAndUpdateThrowStorageUnavailableExceptionWhenTheDriverFails() =
        runTest {
            val driver = FailingDriver(drivers.open()).apply { failing = true }
            val repository = SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined)
            assertFailsWith<StorageUnavailableException> { repository.settings.first() }
            assertFailsWith<StorageUnavailableException> { repository.update { it } }
        }
}
