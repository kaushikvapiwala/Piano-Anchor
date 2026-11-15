package pianoanchor.app

import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.TimeZone
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.core.ports.CalibrationRepositoryPort
import pianoanchor.core.ports.ClockPort
import pianoanchor.core.ports.InstrumentRepositoryPort
import pianoanchor.core.ports.LearnerRepositoryPort
import pianoanchor.core.ports.MetronomePort
import pianoanchor.core.ports.MidiDiscoveryPort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.core.ports.MidiOutputPort
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Passages
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.feature.monitor.MonitorViewModel
import pianoanchor.feature.practice.CalibrationSkips
import pianoanchor.feature.practice.CalibrationViewModel
import pianoanchor.feature.practice.ClickSetting
import pianoanchor.feature.practice.PracticeViewModel
import pianoanchor.feature.practice.ResultsViewModel
import pianoanchor.platform.androidaudio.AndroidMetronomeAdapter
import pianoanchor.platform.androidmidi.AndroidClockAdapter
import pianoanchor.platform.androidmidi.AndroidMidiAdapter
import pianoanchor.platform.storage.SqlAttemptRepositoryAdapter
import pianoanchor.platform.storage.SqlCalibrationRepositoryAdapter
import pianoanchor.platform.storage.SqlInstrumentRepositoryAdapter
import pianoanchor.platform.storage.SqlLearnerRepositoryAdapter
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
        single<TimeZone> { TimeZone.currentSystemDefault() }
        single { ClickSetting() }
        single { CalibrationSkips() }
        single<MetronomePort> { AndroidMetronomeAdapter(androidContext(), get()) }
        single { createPianoAnchorDatabaseDriver(androidContext()) }
        single<InstrumentRepositoryPort> { SqlInstrumentRepositoryAdapter(get(), Dispatchers.IO) }
        single<AttemptRepositoryPort> { SqlAttemptRepositoryAdapter(get(), Dispatchers.IO) }
        single<CalibrationRepositoryPort> { SqlCalibrationRepositoryAdapter(get(), Dispatchers.IO) }
        single<LearnerRepositoryPort> { SqlLearnerRepositoryAdapter(get(), Dispatchers.IO) }
        viewModel { MonitorViewModel(get(), get(), get(), get(), get(), get()) }
        viewModel { (device: MidiDevice, rung: TempoRung) ->
            PracticeViewModel(
                device,
                Passages.CDE,
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                LearnerModel(Curriculum.graph, get()),
                get(),
                get(),
                get(),
                rung,
            ) {
                Uuid.random().toString()
            }
        }
        viewModel { (device: MidiDevice) -> CalibrationViewModel(device, get(), get(), get(), get()) }
        viewModel { (attemptId: String) -> ResultsViewModel(attemptId, get()) }
    }
