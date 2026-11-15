package pianoanchor.feature.settings

import pianoanchor.core.model.Settings

/**
 * What the settings screen shows: the stored [settings] (the defaults until they are read) and whether the store
 * failed ([storageFailed]), which puts the error card at the top.
 */
public data class SettingsState(
    val settings: Settings = Settings(),
    val storageFailed: Boolean = false,
)
