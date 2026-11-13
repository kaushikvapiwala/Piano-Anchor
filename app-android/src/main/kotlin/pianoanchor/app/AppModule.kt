package pianoanchor.app

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pianoanchor.core.ports.ClockPort
import pianoanchor.core.ports.MidiDiscoveryPort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.feature.monitor.MonitorViewModel
import pianoanchor.platform.androidmidi.AndroidClockAdapter
import pianoanchor.platform.androidmidi.AndroidMidiAdapter
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Composition root: adapters bound to the ports the features need. No logic here. */
val appModule =
    module {
        single { AndroidMidiAdapter(androidContext()) }
        single<MidiInputPort> { get<AndroidMidiAdapter>() }
        single<MidiDiscoveryPort> { get<AndroidMidiAdapter>() }
        single<ClockPort> { AndroidClockAdapter() }
        viewModel { MonitorViewModel(get(), get(), get()) { Instant.now().truncatedTo(ChronoUnit.SECONDS).toString() } }
    }
