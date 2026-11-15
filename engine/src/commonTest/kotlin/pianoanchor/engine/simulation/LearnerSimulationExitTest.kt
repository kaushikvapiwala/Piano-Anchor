package pianoanchor.engine.simulation

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.RunMode
import pianoanchor.core.model.Skill
import pianoanchor.core.model.SkillGraph
import pianoanchor.core.model.SkillState
import pianoanchor.engine.Tunables
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.scheduler.Fsrs
import pianoanchor.testing.FakeLearnerRepository
import pianoanchor.testing.SimulatedLearner
import pianoanchor.testing.passageOf
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail
import kotlin.time.Instant
import kotlin.time.TimeSource

private const val LAYERS = 6
private const val SKILLS_PER_LAYER = 10
private const val DAYS = 90
private val SEEDS = 1L..20L
private val SKIP_DAYS = 40..49
private const val SKIP_CHECK_DAY = 50
private const val BACKLOG_CLEAR_DAYS = 10
private const val PRACTICE_RUNS_ON_NEW = 3
private const val GRAPH_SEED = 2026L
private const val RETENTION_MIN_AGE_DAYS = 14
private const val RETENTION_TARGET = 0.90
private const val POOLED_TOLERANCE = 0.05
private const val SEED_TOLERANCE = 0.12
private const val LOAD_FROM_DAY = 15
private const val MEAN_LOAD_SHARE = 0.75
private const val MIN_LEARNED_SHARE = 0.80
private const val MIN_SKIP_RUNS_WITH_BACKLOG = 15
private const val GUARD_FIRST_DAY = 15
private const val GUARD_LAST_DAY = 39
private const val GUARD_LOAD_SHARE = 0.5
private val START = LocalDate(2026, 1, 1)

/** One simulated day: what the plan offered and how the checks went. */
private class DayLog(
    val day: Int,
    val practised: Boolean,
    val due: Int,
    val new: Int,
    val recalled: Int,
    val checks: Int,
    val learned: Int,
    /** Skills due on or before the day that the plan did not offer. */
    val backlog: Int,
    /** Skills due on or before the day, before the plan. */
    val dueTotal: Int,
)

private class Run(
    val seed: Long,
    val log: List<DayLog>,
    /** Every due-skill review: its age in days since introduction and whether it was recalled. */
    val reviews: List<Pair<Int, Boolean>>,
    val introduced: Int,
    val learned: Int,
    /** Skills introduced before all their prerequisites were learned. */
    val orderViolations: List<String>,
    /** Offered due skills that were less overdue than one left out on day 50 (empty when ordered right). */
    val orderOnSkipDay: List<String>,
)

/** The sample graph: 6 layers of 10 original skills, each after the first with 1-2 prerequisites from the layer
 *  above. */
private fun sampleGraph(): SkillGraph {
    val random = Random(GRAPH_SEED)
    val skills =
        (0 until LAYERS).flatMap { layer ->
            (0 until SKILLS_PER_LAYER).map { i ->
                val above = (0 until SKILLS_PER_LAYER).map { "s${layer - 1}-$it" }
                val count = 1 + random.nextInt(2)
                val prerequisites = if (layer == 0) emptySet() else above.shuffled(random).take(count).toSet()
                Skill("s$layer-$i", "Skill $layer-$i", prerequisites)
            }
        }
    return SkillGraph(skills)
}

private fun attemptOf(
    id: String,
    band: Band,
) = Attempt(
    id = id,
    passageId = "sim:passage",
    deviceId = "sim-piano",
    startedAt = Instant.parse("2026-01-01T10:00:00Z"),
    durationNanos = 1,
    stepCount = 1,
    wrongNotes = 0,
    incompleteChords = 0,
    accuracy = 1.0,
    band = band,
    events = emptyList(),
    // A simulated cold check is an unaided perform run: timed, no hint.
    tempoBpm = COLD_CHECK_BPM,
    playedNotes = emptyList(),
    hintShown = false,
    mode = RunMode.PERFORM,
)

private const val COLD_CHECK_BPM = 60

private suspend fun simulate(
    seed: Long,
    graph: SkillGraph,
    skipDays: IntRange?,
): Run {
    val repository = FakeLearnerRepository()
    val model = LearnerModel(graph, repository)
    val learner = SimulatedLearner(seed, graph)
    val fsrs = Fsrs()
    val log = ArrayList<DayLog>()
    val reviews = ArrayList<Pair<Int, Boolean>>()
    val violations = ArrayList<String>()
    var orderOnSkipDay = emptyList<String>()
    var attempts = 0

    suspend fun check(
        skillId: String,
        day: LocalDate,
        practiceRuns: Int,
    ): Band {
        val band = learner.coldCheck(skillId, day)
        repeat(practiceRuns) { learner.practice(skillId) }
        val passage = passageOf(setOf(MidiNote(60)), id = "sim:$skillId", skills = setOf(skillId))
        model.record(passage, attemptOf("attempt-${++attempts}", band), day)
        return band
    }

    for (index in 0 until DAYS) {
        val day = START.plus(index, DateTimeUnit.DAY)
        val before = repository.states()
        val dueTotal = before.values.count { it.dueDay <= day }
        if (skipDays != null && index in skipDays) {
            log += DayLog(index, false, 0, 0, 0, 0, before.values.count { it.learned }, dueTotal, dueTotal)
            continue
        }
        val plan = model.plan(day)
        if (index == SKIP_CHECK_DAY) orderOnSkipDay = overdueMisorder(before, plan.due, day, fsrs)
        var recalled = 0
        for (due in plan.due) {
            val age = due.introducedDay.daysUntil(day)
            val band = check(due.skillId, day, 0)
            val ok = band != Band.KEEP_PRACTISING
            reviews += age to ok
            if (ok) recalled++
        }
        val learnedBefore = before.values.filter { it.learned }.mapTo(HashSet()) { it.skillId }
        for (skill in plan.new) {
            if (!learnedBefore.containsAll(skill.prerequisites)) violations += "${skill.id} on day $index"
            if (check(skill.id, day, PRACTICE_RUNS_ON_NEW) != Band.KEEP_PRACTISING) recalled++
        }
        val after = repository.states()
        log +=
            DayLog(
                index,
                true,
                plan.due.size,
                plan.new.size,
                recalled,
                plan.due.size + plan.new.size,
                after.values.count { it.learned },
                dueTotal - plan.due.size,
                dueTotal,
            )
    }
    val states = repository.states()
    return Run(seed, log, reviews, states.size, states.values.count { it.learned }, violations, orderOnSkipDay)
}

/** Offered skills that were less overdue (higher retrievability) than a due skill the plan left out. */
private fun overdueMisorder(
    states: Map<String, SkillState>,
    offered: List<SkillState>,
    day: LocalDate,
    fsrs: Fsrs,
): List<String> {
    fun retrievability(state: SkillState) = fsrs.retrievability(state.memory, state.lastReviewDay.daysUntil(day))
    val offeredIds = offered.map { it.skillId }.toSet()
    val leftOut = states.values.filter { it.dueDay <= day && it.skillId !in offeredIds }
    val mostRetrievableOffered = offered.maxOfOrNull(::retrievability)
    val leastRetrievableLeftOut = leftOut.minOfOrNull(::retrievability)
    val misordered =
        mostRetrievableOffered != null && leastRetrievableLeftOut != null &&
            mostRetrievableOffered > leastRetrievableLeftOut
    return if (misordered) offered.map { it.skillId } else emptyList()
}

private fun table(run: Run): String =
    buildString {
        appendLine("seed ${run.seed}: day | due | new | offered | success | learned | backlog")
        for (d in run.log.filter { it.practised }) {
            val rate = if (d.checks == 0) "-" else "%.2f".format(d.recalled.toDouble() / d.checks)
            appendLine("  ${d.day} | ${d.due} | ${d.new} | ${d.due + d.new} | $rate | ${d.learned} | ${d.backlog}")
        }
    }

private fun List<Pair<Int, Boolean>>.qualifying() = filter { (age, _) -> age >= RETENTION_MIN_AGE_DAYS }

private fun Double.f() = "%.3f".format(this)

private val graph = sampleGraph()
private val started = TimeSource.Monotonic.markNow()

/** All runs, simulated once and shared by the tests (JUnit makes a new test instance per test). */
private val normal: List<Run> by lazy { SEEDS.map { runOf(it, null) } }
private val skip: List<Run> by lazy { SEEDS.map { runOf(it, SKIP_DAYS) } }

private fun runOf(
    seed: Long,
    skipDays: IntRange?,
): Run {
    var result: Run? = null
    runTest { result = simulate(seed, graph, skipDays) }
    return checkNotNull(result)
}

class LearnerSimulationExitTest {
    private val cap = Tunables.MAX_DUE_PER_DAY
    private val total = graph.skills.size

    private fun failSeeds(
        what: String,
        runs: List<Run>,
    ): Nothing = fail("$what\n" + runs.joinToString("\n") { table(it) })

    @Test
    fun `retention near desired`() {
        val rows =
            normal.map { run ->
                val q = run.reviews.qualifying()
                Triple(run, q.count { it.second }, q.size)
            }
        val summary =
            rows.joinToString("\n") { (run, ok, n) ->
                "  seed ${run.seed}: ${(ok.toDouble() / n).f()} ($n reviews)"
            }
        val pooled = rows.sumOf { it.second }.toDouble() / rows.sumOf { it.third }
        println("Retention per seed:\n$summary\n  pooled: ${pooled.f()} (${rows.sumOf { it.third }} reviews)")
        val outliers =
            rows.filter { (_, ok, n) -> n == 0 || abs(ok.toDouble() / n - RETENTION_TARGET) > SEED_TOLERANCE }
        if (outliers.isNotEmpty()) {
            val seeds = outliers.map { it.first.seed }
            failSeeds("Retention outside 0.90 +- $SEED_TOLERANCE on seeds $seeds\n$summary", outliers.map { it.first })
        }
        if (abs(pooled - RETENTION_TARGET) > POOLED_TOLERANCE) {
            fail("Pooled retention ${pooled.f()} is outside 0.90 +- $POOLED_TOLERANCE\n$summary")
        }
    }

    @Test
    fun `daily load capped`() {
        val bad =
            normal.filter { run ->
                val loads = run.log.filter { it.day >= LOAD_FROM_DAY }.map { it.due }
                loads.any { it > cap } || loads.average() > MEAN_LOAD_SHARE * cap
            }
        println(
            "Load after day $LOAD_FROM_DAY (mean/max, cap $cap):\n" +
                normal.joinToString("\n") { run ->
                    val loads = run.log.filter { it.day >= LOAD_FROM_DAY }.map { it.due }
                    "  seed ${run.seed}: ${loads.average().f()} / ${loads.max()}"
                },
        )
        if (bad.isNotEmpty()) {
            failSeeds("Load over the cap or mean over ${MEAN_LOAD_SHARE * cap} on seeds ${bad.map { it.seed }}", bad)
        }
    }

    @Test
    fun `prerequisites respected and graph progresses`() {
        println(
            "Introduced / learned by day $DAYS:\n" +
                normal.joinToString("\n") { "  seed ${it.seed}: ${it.introduced} / ${it.learned}" },
        )
        val bad =
            normal.filter {
                it.orderViolations.isNotEmpty() || it.introduced < total || it.learned < MIN_LEARNED_SHARE * total
            }
        if (bad.isNotEmpty()) {
            failSeeds(
                "Order or progress failed on seeds ${bad.map { it.seed }}: " +
                    bad.joinToString {
                        "${it.seed}: introduced ${it.introduced}, learned ${it.learned}, early ${it.orderViolations}"
                    },
                bad,
            )
        }
    }

    @Test
    fun `skip recovers in order without overload`() {
        fun clearedAfter(run: Run) =
            run.log
                .firstOrNull { it.day >= SKIP_CHECK_DAY && it.backlog == 0 }
                ?.day
                ?.minus(SKIP_CHECK_DAY)
        println(
            "Skip runs (backlog on day $SKIP_CHECK_DAY, days to clear):\n" +
                skip.joinToString("\n") { run ->
                    val day = run.log[SKIP_CHECK_DAY]
                    "  seed ${run.seed}: due on day $SKIP_CHECK_DAY ${day.dueTotal}, not offered ${day.backlog}, " +
                        "days to clear ${clearedAfter(run)}"
                },
        )
        val bad =
            skip.filter { run ->
                run.orderOnSkipDay.isNotEmpty() ||
                    run.log.any { it.due > cap } ||
                    clearedAfter(run).let { it == null || it > BACKLOG_CLEAR_DAYS }
            }
        if (bad.isNotEmpty()) {
            failSeeds(
                "Skip recovery failed on seeds ${bad.map { it.seed }}: " +
                    bad.joinToString {
                        "${it.seed}: misordered ${it.orderOnSkipDay}, cleared after ${clearedAfter(it)}"
                    },
                bad,
            )
        }
    }

    @Test
    fun `scenarios bind`() {
        val withBacklog = skip.count { it.log[SKIP_CHECK_DAY].dueTotal > cap }
        if (withBacklog < MIN_SKIP_RUNS_WITH_BACKLOG) {
            fail(
                "Scenario too easy: only $withBacklog of ${SEEDS.count()} skip runs have a backlog over $cap on day " +
                    "$SKIP_CHECK_DAY (need $MIN_SKIP_RUNS_WITH_BACKLOG): " +
                    skip.joinToString { "${it.seed}=${it.log[SKIP_CHECK_DAY].dueTotal}" },
            )
        }
        val busy =
            normal.any { run ->
                run.log.any { it.day in GUARD_FIRST_DAY..GUARD_LAST_DAY && it.due >= GUARD_LOAD_SHARE * cap }
            }
        if (!busy) {
            fail(
                "Scenario too easy: no normal run plans ${GUARD_LOAD_SHARE * cap} due reviews on a day in " +
                    "$GUARD_FIRST_DAY..$GUARD_LAST_DAY",
            )
        }
        println("Wall time of the simulation so far: ${started.elapsedNow()}")
    }
}
