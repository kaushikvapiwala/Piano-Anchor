package pianoanchor.app

import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.core.ports.ClockPort
import pianoanchor.core.ports.InstrumentRepositoryPort
import pianoanchor.core.ports.MidiDiscoveryPort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.core.ports.MidiOutputPort
import pianoanchor.engine.content.Passages
import pianoanchor.feature.monitor.MonitorViewModel
import pianoanchor.feature.practice.PracticeViewModel
import pianoanchor.platform.androidmidi.AndroidClockAdapter
import pianoanchor.platform.androidmidi.AndroidMidiAdapter
import pianoanchor.platform.storage.SqlAttemptRepositoryAdapter
import pianoanchor.platform.storage.SqlInstrumentRepositoryAdapter
import pianoanchor.platform.storage.createPianoAnchorDatabaseDriver
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Composition root: adapters bound to the ports the features need. No logic here. */
val appModule =
    module {
        single { AndroidMidiAdapter(androidContext()) }
        single<MidiInputPort> { get<AndroidMidiAdapter>() }
        single<MidiOutputPort> { get<AndroidMidiAdapter>() }
        single<MidiDiscoveryPort> { get<AndroidMidiAdapter>() }
        single<ClockPort> { AndroidClockAdapter() }
        single<Clock> { Clock.System }
        single { createPianoAnchorDatabaseDriver(androidContext()) }
        single<InstrumentRepositoryPort> { SqlInstrumentRepositoryAdapter(get(), Dispatchers.IO) }
        single<AttemptRepositoryPort> { SqlAttemptRepositoryAdapter(get(), Dispatchers.IO) }
        viewModel { MonitorViewModel(get(), get(), get(), get(), get(), get()) }
        viewModel { (device: MidiDevice) ->
            PracticeViewModel(device, Passages.CDE, get(), get(), get()) { Uuid.random().toString() }
        }
    }
