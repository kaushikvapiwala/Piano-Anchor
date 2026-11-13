package pianoanchor.feature.monitor

import pianoanchor.core.model.MidiDevice

/** What the user can do on the [MonitorScreen]; the composition root wires these to the ViewModel and platform. */
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
)
