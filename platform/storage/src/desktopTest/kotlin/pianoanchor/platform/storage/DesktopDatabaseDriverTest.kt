package pianoanchor.platform.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.Band
import pianoanchor.core.model.Instrument
import pianoanchor.core.model.PassageComplete
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class DesktopDatabaseDriverTest {
    private val directory: File = createTempDirectory("piano-anchor-db").toFile()
    private val file = File(directory, "nested/piano-anchor.db")

    @AfterTest
    fun deleteDirectory() {
        directory.deleteRecursively()
    }

    @Test
    fun dataSurvivesReopeningTheFile() =
        runTest {
            val seen = Instant.fromEpochMilliseconds(1_000)
            val piano = Instrument("usb-1", "Piano", identity = null, firstSeen = seen, lastSeen = seen)
            createPianoAnchorDatabaseDriver(file).use { driver ->
                SqlInstrumentRepositoryAdapter(driver, Dispatchers.Unconfined).upsert(piano)
            }
            createPianoAnchorDatabaseDriver(file).use { driver ->
                assertEquals(piano, SqlInstrumentRepositoryAdapter(driver, Dispatchers.Unconfined).get("usb-1"))
            }
        }

    @Test
    fun foreignKeysAreOn() {
        createPianoAnchorDatabaseDriver(file).use { driver ->
            assertFails {
                driver.exec(
                    "INSERT INTO attempt_event(attempt_id, seq, kind, t_nanos, step_index) " +
                        "VALUES ('none', 0, 'step_correct', 0, 0)",
                )
            }
        }
    }

    @Test
    fun migrationFromVersion1KeepsOldAttemptsWithNullTempoAndNoNotes() =
        runTest {
            file.parentFile.mkdirs()
            // The committed version-1 schema file, with an attempt and an event written the way version 1 did.
            File("src/commonMain/sqldelight/databases/1.db").copyTo(file)
            JdbcSqliteDriver("jdbc:sqlite:${file.path}").use { old ->
                old.exec("PRAGMA user_version = 1")
                old.exec(
                    "INSERT INTO attempt VALUES ('old-1', 'builtin:cde', 'usb-1', 1234, 5000, 3, 0, 0, 1.0, 'CLEAN')",
                )
                old.exec("INSERT INTO attempt_event VALUES ('old-1', 0, 'passage_complete', 0, 2, NULL, NULL)")
            }

            createPianoAnchorDatabaseDriver(file).use { driver ->
                val repository = SqlAttemptRepositoryAdapter(driver, Dispatchers.Unconfined)
                val old = assertNotNull(repository.get("old-1"))
                assertNull(old.tempoBpm)
                assertEquals(emptyList(), old.playedNotes)
                assertEquals(listOf(PassageComplete(0, 2)), old.events)
                assertEquals(Band.CLEAN, old.band)
                assertEquals(emptySet(), repository.cleanTempos("builtin:cde"))
            }
        }

    @Test
    fun migration2To3AddsTheCalibrationTableAndANullOffsetToOldAttempts() =
        runTest {
            file.parentFile.mkdirs()
            // The committed version-2 schema file, with a timed attempt written the way version 2 did.
            File("src/commonMain/sqldelight/databases/2.db").copyTo(file)
            JdbcSqliteDriver("jdbc:sqlite:${file.path}").use { old ->
                old.exec("PRAGMA user_version = 2")
                old.exec(
                    "INSERT INTO attempt VALUES " +
                        "('old-2', 'builtin:cde', 'usb-1', 1234, 5000, 3, 0, 0, 1.0, 'CLEAN', 80)",
                )
            }

            createPianoAnchorDatabaseDriver(file).use { driver ->
                val attempts = SqlAttemptRepositoryAdapter(driver, Dispatchers.Unconfined)
                val old = assertNotNull(attempts.get("old-2"))
                assertEquals(80, old.tempoBpm)
                assertNull(old.latencyOffsetNanos)

                val calibration = SqlCalibrationRepositoryAdapter(driver, Dispatchers.Unconfined)
                assertNull(calibration.latencyOffsetNanos("usb-1"))
                calibration.setLatencyOffsetNanos("usb-1", 38_000_000)
                assertEquals(38_000_000L, calibration.latencyOffsetNanos("usb-1"))
            }
        }

    @Test
    fun migration3To4AddsEmptyReviewAndSkillStateTables() {
        file.parentFile.mkdirs()
        // The committed version-3 schema file, with a calibration row written the way version 3 did.
        File("src/commonMain/sqldelight/databases/3.db").copyTo(file)
        JdbcSqliteDriver("jdbc:sqlite:${file.path}").use { old ->
            old.exec("PRAGMA user_version = 3")
            old.exec("INSERT INTO calibration VALUES ('usb-1', 38000000)")
        }

        createPianoAnchorDatabaseDriver(file).use { driver ->
            assertEquals(0, driver.count("review"))
            assertEquals(0, driver.count("skill_state"))
            assertEquals(1, driver.count("calibration"))
        }
    }

    @Test
    fun migration4To5StartsRecalledChecksAtTheCleanCount() =
        runTest {
            file.parentFile.mkdirs()
            // The committed version-4 schema file, with a skill state written the way version 4 did.
            File("src/commonMain/sqldelight/databases/4.db").copyTo(file)
            JdbcSqliteDriver("jdbc:sqlite:${file.path}").use { old ->
                old.exec("PRAGMA user_version = 4")
                old.exec(
                    "INSERT INTO skill_state VALUES " +
                        "('pitch:c4', 3.5, 6.25, '2026-11-01', '2026-11-03', '2026-11-10', 2, 1)",
                )
            }

            createPianoAnchorDatabaseDriver(file).use { driver ->
                val state = SqlLearnerRepositoryAdapter(driver, Dispatchers.Unconfined).states().getValue("pitch:c4")
                assertEquals(2, state.cleanColdChecks)
                assertEquals(2, state.recalledColdChecks)
            }
        }

    @Test
    fun migration5To6AddsAnEmptySettingTable() {
        file.parentFile.mkdirs()
        // The committed version-5 schema file, with a skill state written the way version 5 did.
        File("src/commonMain/sqldelight/databases/5.db").copyTo(file)
        JdbcSqliteDriver("jdbc:sqlite:${file.path}").use { old ->
            old.exec("PRAGMA user_version = 5")
            old.exec(
                "INSERT INTO skill_state VALUES " +
                    "('pitch:c4', 3.5, 6.25, '2026-11-01', '2026-11-03', '2026-11-10', 2, 1, 2)",
            )
        }

        createPianoAnchorDatabaseDriver(file).use { driver ->
            assertEquals(0, driver.count("setting"))
            assertEquals(1, driver.count("skill_state"))
        }
    }

    @Test
    fun migration9To10MarksOldReviewsUnaidedAndBackfillsTheUnaidedRecalledChecks() =
        runTest {
            file.parentFile.mkdirs()
            // The committed version-9 schema file, with reviews and states written the way version 9 did.
            File("src/commonMain/sqldelight/databases/9.db").copyTo(file)
            JdbcSqliteDriver("jdbc:sqlite:${file.path}").use { old ->
                old.exec("PRAGMA user_version = 9")
                old.exec("INSERT INTO review VALUES ('pitch:c4', '2026-11-01', 'HARD', 'a-1')")
                old.exec("INSERT INTO review VALUES ('pitch:d4', '2026-11-01', 'GOOD', 'placement')")
                old.exec(
                    "INSERT INTO skill_state VALUES " +
                        "('pitch:c4', 3.5, 6.25, '2026-11-01', '2026-11-03', '2026-11-10', 1, 0, 3)",
                )
                old.exec(
                    "INSERT INTO skill_state VALUES " +
                        "('pitch:d4', 3.5, 6.25, '2026-11-01', '2026-11-01', '2026-11-10', 0, 1, 0)",
                )
            }

            createPianoAnchorDatabaseDriver(file).use { driver ->
                val repository = SqlLearnerRepositoryAdapter(driver, Dispatchers.Unconfined)
                assertEquals(true, repository.reviews("pitch:c4").single().unaided)
                val states = repository.states()
                assertEquals(3, states.getValue("pitch:c4").unaidedRecalledChecks)
                assertEquals(false, states.getValue("pitch:c4").learned)
                assertEquals(0, states.getValue("pitch:d4").unaidedRecalledChecks)
                assertEquals(true, states.getValue("pitch:d4").learned)
            }
        }
}
