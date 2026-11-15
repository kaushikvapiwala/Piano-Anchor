package pianoanchor.engine.content

import pianoanchor.core.model.Skill

/** Skills built into the app. Titles live here, like passage titles, and are localised with them later. */
public object Skills {
    /** Playing single notes with the right hand on C, D and E, thumb on middle C. */
    public val RH_C_POSITION: Skill = Skill("rh-c-position", "Right hand, C position", emptySet())
}
