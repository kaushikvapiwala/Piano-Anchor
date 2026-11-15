package pianoanchor.core.model

import kotlinx.datetime.LocalDate

/** Why a passage may ship in the app. */
public enum class RightsStatus {
    /** Written for Piano Anchor. */
    ORIGINAL,

    /** A work whose copyright has expired or never applied. */
    PUBLIC_DOMAIN,
}

/**
 * Why a passage may ship. PUBLIC_DOMAIN needs [origin] (work, author or "traditional", date) and [basis] (why it is
 * public domain); every passage's arrangement is original to Piano Anchor. [checkedOn] = when the record was made.
 */
public data class Rights(
    val status: RightsStatus,
    val origin: String,
    val basis: String,
    val checkedOn: LocalDate,
) {
    init {
        if (status == RightsStatus.PUBLIC_DOMAIN) {
            require(origin.isNotBlank()) { "a public domain record must name its origin" }
            require(basis.isNotBlank()) { "a public domain record must say why it is public domain" }
        }
    }
}
