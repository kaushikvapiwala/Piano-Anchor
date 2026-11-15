package pianoanchor.feature.monitor

import pianoanchor.core.model.MidiDevice

/**
 * What the user can do on the [MonitorScreen]; the composition root wires these to the ViewModel and platform.
 * A null [onPractice] or [onCalibrate] hides its button (e.g. while the onboarding is open).
 */
public data class MonitorActions(
    val onSelect: (MidiDevice) -> Unit = {},
    val onScan: () -> Unit = {},
    val onConnect: (MidiDevice) -> Unit = {},
    val onStartRecording: () -> Unit = {},
    val onStopRecording: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onEnableBluetooth: () -> Unit = {},
    val onDismissError: () -> Unit = {},
    val onPractice: ((MidiDevice) -> Unit)? = {},
    val onCalibrate: ((MidiDevice) -> Unit)? = {},
)
