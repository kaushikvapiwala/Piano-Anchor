import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

// Pure Kotlin Multiplatform library (domain, ports, engine, test support).
// Must stay free of Android/Compose/DI imports and keeps 100% line and branch coverage (owner rule).
// Exceptions must be annotated with @ExcludeFromCoverage("reason") so they are visible in review.
plugins {
    id("pianoanchor.kmp-base")
}

kover {
    reports {
        filters { excludes { annotatedBy("*ExcludeFromCoverage") } }
        verify {
            rule("100% line coverage") {
                bound {
                    minValue.set(100)
                    coverageUnits.set(CoverageUnit.LINE)
                }
            }
            rule("100% branch coverage") {
                bound {
                    minValue.set(100)
                    coverageUnits.set(CoverageUnit.BRANCH)
                }
            }
        }
    }
}
