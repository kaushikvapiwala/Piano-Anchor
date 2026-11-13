package pianoanchor.app

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pianoanchor.core.ports.ClockPort
import pianoanchor.core.ports.InstrumentRepositoryPort
import pianoanchor.core.ports.MidiDiscoveryPort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.core.ports.MidiOutputPort
import pianoanchor.feature.monitor.MonitorViewModel
import pianoanchor.platform.androidmidi.AndroidClockAdapter
import pianoanchor.platform.androidmidi.AndroidMidiAdapter
import pianoanchor.testing.FakeInstrumentRepository
import kotlin.time.Clock

/** Composition root: adapters bound to the ports the features need. No logic here. */
val appModule =
    module {
        single { AndroidMidiAdapter(androidContext()) }
        single<MidiInputPort> { get<AndroidMidiAdapter>() }
        single<MidiOutputPort> { get<AndroidMidiAdapter>() }
        single<MidiDiscoveryPort> { get<AndroidMidiAdapter>() }
        single<ClockPort> { AndroidClockAdapter() }
        single<Clock> { Clock.System }
        // In memory until the SQLDelight adapter lands (roadmap slice 1.8); then :testing leaves the app.
        single<InstrumentRepositoryPort> { FakeInstrumentRepository() }
        viewModel { MonitorViewModel(get(), get(), get(), get(), get(), get()) }
    }
