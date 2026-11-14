package pianoanchor.testing

import pianoanchor.core.model.ErrorKind
import pianoanchor.core.model.Finding

/**
 * How well an analyser's findings match the injected labels (`docs/algorithms/performance-analysis.md`).
 * [perKind] maps each injected kind to (matched, injected); [falseAlarms] counts findings on clean takes, which
 * have [cleanNotes] played notes. A rate over nothing ([overall] with nothing injected, [kindRate] of a kind never
 * injected, [falseAlarmRate] without clean notes) throws [IllegalStateException], so a harness bug can't pass.
 */
data class ClassificationScore(
    val perKind: Map<ErrorKind, Pair<Int, Int>>,
    val falseAlarms: Int,
    val cleanNotes: Int,
) {
    init {
        perKind.forEach { (kind, counts) ->
            require(counts.second > 0 && counts.first in 0..counts.second) {
                "$kind: matched (${counts.first}) must be in 0..injected (${counts.second}), injected positive"
            }
        }
        require(falseAlarms >= 0 && cleanNotes >= 0) {
            "false alarms ($falseAlarms) and clean notes ($cleanNotes) must not be negative"
        }
    }

    /** Matched labels / injected labels across all kinds. */
    val overall: Double
        get() {
            check(perKind.isNotEmpty()) { "no errors were injected" }
            return perKind.values.sumOf { it.first }.toDouble() / perKind.values.sumOf { it.second }
        }

    /** Matched / injected labels of [kind]. */
    fun kindRate(kind: ErrorKind): Double {
        val (matched, injected) = checkNotNull(perKind[kind]) { "no $kind errors were injected" }
        return matched.toDouble() / injected
    }

    /** Findings on clean takes / played notes in clean takes. */
    val falseAlarmRate: Double
        get() {
            check(cleanNotes > 0) { "no clean takes were scored" }
            return falseAlarms.toDouble() / cleanNotes
        }

    companion object {
        /**
         * Scores each take's findings against its labels. A label is matched by a finding of the same kind at the
         * same score note, one to one. On error takes unmatched findings are ignored (an error can cause a second,
         * legitimate finding); on clean takes (no labels) every finding is a false alarm.
         */
        fun of(results: List<Pair<LabelledTake, List<Finding>>>): ClassificationScore {
            val matched = HashMap<ErrorKind, Int>()
            val injected = HashMap<ErrorKind, Int>()
            var falseAlarms = 0
            var cleanNotes = 0
            results.forEach { (take, findings) ->
                if (take.labels.isEmpty()) {
                    falseAlarms += findings.size
                    cleanNotes += take.notes.size
                }
                val unmatched = findings.mapTo(ArrayList()) { it.kind to it.at }
                take.labels.forEach { label ->
                    injected[label.kind] = injected.getOrElse(label.kind) { 0 } + 1
                    if (unmatched.remove(label.kind to label.at)) {
                        matched[label.kind] =
                            matched.getOrElse(label.kind) { 0 } + 1
                    }
                }
            }
            return ClassificationScore(
                perKind = injected.mapValues { (kind, count) -> matched.getOrElse(kind) { 0 } to count },
                falseAlarms = falseAlarms,
                cleanNotes = cleanNotes,
            )
        }
    }
}
