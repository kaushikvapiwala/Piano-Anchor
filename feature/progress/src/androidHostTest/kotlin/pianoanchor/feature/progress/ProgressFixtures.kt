package pianoanchor.feature.progress

import kotlinx.datetime.LocalDate
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.PassageComplete
import pianoanchor.core.model.Skill
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.progress.LaneDot
import pianoanchor.engine.session.WeekStatus
import kotlin.time.Instant

internal const val SDK = 36

internal val WEEK =
    WeekStatus(
        weekStart = LocalDate(2026, 11, 16),
        daysPlayed = 3,
        goalDays = 4,
        paused = false,
        pausesLeft = 1,
        canPause = true,
        streakWeeks = 2,
    )

private fun take(
    id: String,
    at: String,
) = Attempt(
    id = id,
    passageId = Curriculum.passages.first { it.piece }.id,
    deviceId = "usb-1",
    startedAt = Instant.parse(at),
    durationNanos = 1,
    stepCount = 1,
    wrongNotes = 0,
    incompleteChords = 0,
    accuracy = 1.0,
    band = Band.CLEAN,
    events = listOf(PassageComplete(0, 0)),
    tempoBpm = null,
    playedNotes = emptyList(),
)

internal val COMPARE =
    Compare(
        passage = Curriculum.passages.first { it.piece },
        day1 = take("a-1", "2026-11-03T09:00:00Z"),
        today = take("a-2", "2026-11-18T09:00:00Z"),
        day1Date = LocalDate(2026, 11, 3),
        todayDate = LocalDate(2026, 11, 18),
        day1Lane = listOf(LaneDot(0, true), LaneDot(900, false), LaneDot(2_100, true), LaneDot(3_000, false)),
        todayLane = listOf(LaneDot(0, true), LaneDot(1_000, true), LaneDot(2_000, true), LaneDot(3_000, true)),
    )

internal val STATE =
    ProgressState(
        week = WEEK,
        minutesByDay = listOf(12, 0, 7, 0, 0, 0, 0),
        todayIndex = 2,
        learnedThisWeek =
            listOf(
                Skill("steps", "Steps and skips", emptySet()),
                Skill("bars", "Bars and counting", emptySet()),
            ),
        compare = COMPARE,
    )
