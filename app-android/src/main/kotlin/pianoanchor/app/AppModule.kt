package pianoanchor.app

import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.TimeZone
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.Skill
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.core.ports.CalibrationRepositoryPort
import pianoanchor.core.ports.ClockPort
import pianoanchor.core.ports.InstrumentRepositoryPort
import pianoanchor.core.ports.LearnerRepositoryPort
import pianoanchor.core.ports.MetronomePort
import pianoanchor.core.ports.MidiDiscoveryPort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.core.ports.MidiOutputPort
import pianoanchor.core.ports.SettingsRepositoryPort
import pianoanchor.core.ports.TonePort
import pianoanchor.core.ports.UpdateCheckPort
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.feature.ear.EarViewModel
import pianoanchor.feature.monitor.MonitorViewModel
import pianoanchor.feature.onboarding.OnboardingViewModel
import pianoanchor.feature.practice.CalibrationSkips
import pianoanchor.feature.practice.CalibrationViewModel
import pianoanchor.feature.practice.PracticeViewModel
import pianoanchor.feature.practice.ResultsViewModel
import pianoanchor.feature.settings.SettingsViewModel
import pianoanchor.feature.today.TodayViewModel
import pianoanchor.platform.androidaudio.AndroidMetronomeAdapter
import pianoanchor.platform.androidaudio.AndroidToneAdapter
import pianoanchor.platform.androidmidi.AndroidClockAdapter
import pianoanchor.platform.androidmidi.AndroidMidiAdapter
import pianoanchor.platform.androidnetwork.GitHubUpdateCheckAdapter
import pianoanchor.platform.storage.SqlAttemptRepositoryAdapter
import pianoanchor.platform.storage.SqlCalibrationRepositoryAdapter
import pianoanchor.platform.storage.SqlInstrumentRepositoryAdapter
import pianoanchor.platform.storage.SqlLearnerRepositoryAdapter
import pianoanchor.platform.storage.SqlSettingsRepositoryAdapter
import pianoanchor.platform.storage.createPianoAnchorDatabaseDriver
import kotlin.random.Random
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
        single { CalibrationSkips() }
        single<MetronomePort> { AndroidMetronomeAdapter(androidContext(), get()) }
        single<TonePort> { AndroidToneAdapter(androidContext()) }
        single { createPianoAnchorDatabaseDriver(androidContext()) }
        single<InstrumentRepositoryPort> { SqlInstrumentRepositoryAdapter(get(), Dispatchers.IO) }
        single<AttemptRepositoryPort> { SqlAttemptRepositoryAdapter(get(), Dispatchers.IO) }
        single<CalibrationRepositoryPort> { SqlCalibrationRepositoryAdapter(get(), Dispatchers.IO) }
        single<LearnerRepositoryPort> { SqlLearnerRepositoryAdapter(get(), Dispatchers.IO) }
        single<SettingsRepositoryPort> { SqlSettingsRepositoryAdapter(get(), Dispatchers.IO) }
        single<UpdateCheckPort> { GitHubUpdateCheckAdapter(BuildConfig.VERSION_NAME, BuildConfig.UPDATE_REPOSITORY) }
        factory { LearnerModel(Curriculum.graph, get()) }
        viewModel { MonitorViewModel(get(), get(), get(), get(), get(), get()) }
        viewModel { (device: MidiDevice, passageId: String, rung: TempoRung) ->
            PracticeViewModel(
                device,
                requireNotNull(Curriculum.passage(passageId)) { "unknown passage $passageId" },
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                rung,
            ) {
                Uuid.random().toString()
            }
        }
        viewModel { (device: MidiDevice, skills: List<Skill>) ->
            EarViewModel(
                device,
                skills,
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                Random(get<Clock>().now().toEpochMilliseconds()),
            ) {
                Uuid.random().toString()
            }
        }
        viewModel { (device: MidiDevice) -> CalibrationViewModel(device, get(), get(), get(), get()) }
        viewModel { (attemptId: String) -> ResultsViewModel(attemptId, get()) }
        viewModel { SettingsViewModel(get()) }
        viewModel {
            OnboardingViewModel(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
            )
        }
        viewModel { TodayViewModel(get(), get(), get(), get(), get(), get(), get(), get(), BuildConfig.VERSION_NAME) }
    }
