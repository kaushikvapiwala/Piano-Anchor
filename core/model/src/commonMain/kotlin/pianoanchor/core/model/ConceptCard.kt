package pianoanchor.core.model

/** What a concept is, why it exists, how it's used and when the learner meets it; one line each, none blank. */
public data class ConceptCard(
    val skillId: String,
    val what: String,
    val why: String,
    val how: String,
    val whenMet: String,
) {
    init {
        require(what.isNotBlank()) { "concept card \"$skillId\" has a blank what" }
        require(why.isNotBlank()) { "concept card \"$skillId\" has a blank why" }
        require(how.isNotBlank()) { "concept card \"$skillId\" has a blank how" }
        require(whenMet.isNotBlank()) { "concept card \"$skillId\" has a blank whenMet" }
    }
}
