package pianoanchor.engine

/**
 * Every threshold the engine uses lives here, each with "Why:" and "Source:" in its KDoc.
 * Values are priors: tune them from the learner's own data.
 */
public object Tunables {
    /**
     * Notes whose onsets fall within this many milliseconds of a cluster's first note form one chord.
     *
     * Why: a chord is one physical gesture, so the window is absolute (not tempo-scaled, not widened
     * for beginners).
     *
     * Source: practice-mode scorers group chord tones within 50–90 ms; score-alignment research uses a
     * tighter 35 ms simultaneity threshold (Nakamura et al., ISMIR 2017). 90 ms suits beginners.
     */
    public const val CHORD_CLUSTER_MS: Int = 90
}
