package pianoanchor.platform.storage

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import pianoanchor.core.model.DailyBudget
import pianoanchor.core.model.LessonStep
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.NoteNames
import pianoanchor.core.model.Release
import pianoanchor.core.model.Settings
import pianoanchor.core.model.Strictness
import pianoanchor.core.model.WEEKLY_GOAL_DAYS_RANGE
import pianoanchor.core.ports.SettingsRepositoryPort
import pianoanchor.platform.storage.db.PianoAnchorDatabase
import pianoanchor.platform.storage.db.SettingsQueries

private const val DAILY_BUDGET = "daily_budget"
private const val WEEKLY_GOAL_DAYS = "weekly_goal_days"
private const val CLICK_ON = "click_on"
private const val UPDATE_CHECK_ON = "update_check_on"
private const val ONBOARDED = "onboarded"
private const val LAST_UPDATE_CHECK = "last_update_check"
private const val LATEST_RELEASE = "latest_release"
private const val DISMISSED_UPDATE = "dismissed_update"
private const val STRICTNESS = "strictness"
private const val LAST_PIANO = "last_piano"
private const val LESSON_STEPS = "lesson_steps"
private const val PEDAL_CONTROL_ON = "pedal_control_on"
private const val NOTE_NAMES = "note_names"
private const val PAUSED_WEEKS = "paused_weeks"

/** A stored text that is "not set" (the rows of absent values hold it, so every update writes every row). */
private const val UNSET = ""

/** Separates a piano's id from its name in the `last_piano` row; an id (`ble:<ADDRESS>`) has no line break. */
private const val PIANO_SEPARATOR = "\n"

/** Separates the `skill=STEP` entries of the `lesson_steps` row and a skill's id from its step. */
private const val LESSON_ENTRY_SEPARATOR = "\n"
private const val LESSON_STEP_SEPARATOR = "="

/** Separates the dates of the `paused_weeks` row. */
private const val PAUSED_WEEK_SEPARATOR = ","

/** Separates a release's version from its page URL in the `latest_release` row; neither contains a space. */
private const val RELEASE_SEPARATOR = " "

/**
 * [SettingsRepositoryPort] in the on-device SQLite database, one text row per setting. A missing row reads as the
 * default, and so does a value that can't be read (it is overwritten by the next [update], which writes every row).
 * [driver] comes from `createPianoAnchorDatabaseDriver`; every query runs on [dispatcher] (`Dispatchers.IO` in the
 * app).
 */
public class SqlSettingsRepositoryAdapter(
    driver: SqlDriver,
    private val dispatcher: CoroutineDispatcher,
) : SettingsRepositoryPort {
    private val queries = PianoAnchorDatabase(driver).settingsQueries

    /** Serialises updates, so concurrent ones each see the result of the one before. */
    private val updates = Mutex()

    // Re-runs after every write to `setting`.
    override val settings: Flow<Settings> =
        queries
            .selectAll()
            .asFlow()
            .map { withContext(dispatcher) { queries.read() } }
            .storage("settings")

    override suspend fun update(change: (Settings) -> Settings) {
        updates.withLock {
            withContext(dispatcher) {
                storage("update") {
                    queries.transaction {
                        val updated = change(queries.read())
                        queries.put(DAILY_BUDGET, updated.dailyBudget.name)
                        queries.put(WEEKLY_GOAL_DAYS, updated.weeklyGoalDays.toString())
                        queries.put(CLICK_ON, updated.clickOn.toString())
                        queries.put(UPDATE_CHECK_ON, updated.updateCheckOn.toString())
                        queries.put(ONBOARDED, updated.onboarded.toString())
                        queries.put(LAST_UPDATE_CHECK, updated.lastUpdateCheck.stored())
                        queries.put(LATEST_RELEASE, updated.latestRelease.stored())
                        queries.put(DISMISSED_UPDATE, updated.dismissedUpdate ?: UNSET)
                        queries.put(STRICTNESS, updated.strictness.name)
                        queries.put(LAST_PIANO, updated.lastPiano.stored())
                        queries.put(LESSON_STEPS, updated.lessonSteps.stored())
                        queries.put(PEDAL_CONTROL_ON, updated.pedalControlOn.toString())
                        queries.put(NOTE_NAMES, updated.noteNames.name)
                        queries.put(PAUSED_WEEKS, updated.pausedWeeks.sorted().joinToString(PAUSED_WEEK_SEPARATOR))
                    }
                }
            }
        }
    }
}

/** The stored settings, read at once. */
private fun SettingsQueries.read(): Settings =
    selectAll().executeAsList().associate { it.key to it.text_value }.toSettings()

/** The settings these stored values (by key) hold; each missing or unreadable one is the default. */
private fun Map<String, String>.toSettings(): Settings {
    val defaults = Settings()
    return Settings(
        dailyBudget =
            read(DAILY_BUDGET, defaults.dailyBudget) { name -> DailyBudget.entries.firstOrNull { it.name == name } },
        weeklyGoalDays =
            read(WEEKLY_GOAL_DAYS, defaults.weeklyGoalDays) { text ->
                WEEKLY_GOAL_DAYS_RANGE.firstOrNull { it.toString() == text }
            },
        clickOn = read(CLICK_ON, defaults.clickOn, String::toBooleanStrictOrNull),
        updateCheckOn = read(UPDATE_CHECK_ON, defaults.updateCheckOn, String::toBooleanStrictOrNull),
        onboarded = read(ONBOARDED, defaults.onboarded, String::toBooleanStrictOrNull),
        lastUpdateCheck = this[LAST_UPDATE_CHECK]?.toLocalDateOrNull(),
        latestRelease = this[LATEST_RELEASE]?.toReleaseOrNull(),
        dismissedUpdate = this[DISMISSED_UPDATE]?.takeIf { it != UNSET },
        strictness =
            read(
                STRICTNESS,
                defaults.strictness,
            ) { name -> Strictness.entries.firstOrNull { it.name == name } },
        lastPiano = this[LAST_PIANO]?.toPianoOrNull(),
        lessonSteps = this[LESSON_STEPS]?.toLessonSteps().orEmpty(),
        pedalControlOn = read(PEDAL_CONTROL_ON, defaults.pedalControlOn, String::toBooleanStrictOrNull),
        noteNames = read(NOTE_NAMES, defaults.noteNames) { name -> NoteNames.entries.firstOrNull { it.name == name } },
        pausedWeeks =
            this[PAUSED_WEEKS]
                .orEmpty()
                .split(
                    PAUSED_WEEK_SEPARATOR,
                ).mapNotNullTo(HashSet()) { it.toLocalDateOrNull() },
    )
}

private fun LocalDate?.stored(): String = if (this == null) UNSET else toString()

private fun Release?.stored(): String = if (this == null) UNSET else version + RELEASE_SEPARATOR + pageUrl

private fun MidiDevice?.stored(): String = if (this == null) UNSET else id + PIANO_SEPARATOR + name

private fun Map<String, LessonStep>.stored(): String =
    entries.joinToString(LESSON_ENTRY_SEPARATOR) { (id, step) -> id + LESSON_STEP_SEPARATOR + step.name }

/** The `skill=STEP` entries that can be read; a malformed one is left out. */
private fun String.toLessonSteps(): Map<String, LessonStep> =
    split(LESSON_ENTRY_SEPARATOR)
        .mapNotNull { entry ->
            val parts = entry.split(LESSON_STEP_SEPARATOR)
            val step = parts.getOrNull(1)?.let { name -> LessonStep.entries.firstOrNull { it.name == name } }
            if (parts.size == 2 && parts[0].isNotBlank() && step != null) parts[0] to step else null
        }.toMap()

private fun String.toPianoOrNull(): MidiDevice? {
    val parts = split(PIANO_SEPARATOR, limit = 2)
    return if (parts.size == 2 && parts.none { it.isBlank() }) MidiDevice(parts[0], parts[1]) else null
}

private fun String.toReleaseOrNull(): Release? {
    val parts = split(RELEASE_SEPARATOR)
    return if (parts.size == 2 && parts.none { it.isEmpty() }) Release(parts[0], parts[1]) else null
}

private fun String.toLocalDateOrNull(): LocalDate? = LocalDate.Formats.ISO.parseOrNull(this)

/** The value stored under [key] as [parse] reads it, or [default] if there is none or it is unreadable. */
private fun <T : Any> Map<String, String>.read(
    key: String,
    default: T,
    parse: (String) -> T?,
): T = this[key]?.let(parse) ?: default
