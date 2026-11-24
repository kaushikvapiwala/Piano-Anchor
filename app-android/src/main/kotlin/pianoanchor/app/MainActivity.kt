@file:Suppress("TooManyFunctions") // One small route composable per screen of the app.

package pianoanchor.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import pianoanchor.core.model.BarRange
import pianoanchor.core.model.LessonStep
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.RunMode
import pianoanchor.core.model.Skill
import pianoanchor.engine.content.Passages
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.feature.ear.EarActions
import pianoanchor.feature.ear.EarScreen
import pianoanchor.feature.ear.EarViewModel
import pianoanchor.feature.lesson.LessonActions
import pianoanchor.feature.lesson.LessonScreen
import pianoanchor.feature.lesson.LessonViewModel
import pianoanchor.feature.lesson.QuizActions
import pianoanchor.feature.lesson.QuizScreen
import pianoanchor.feature.lesson.QuizViewModel
import pianoanchor.feature.monitor.MonitorActions
import pianoanchor.feature.monitor.MonitorScreen
import pianoanchor.feature.monitor.MonitorViewModel
import pianoanchor.feature.onboarding.OnboardingActions
import pianoanchor.feature.onboarding.OnboardingScreen
import pianoanchor.feature.onboarding.OnboardingViewModel
import pianoanchor.feature.practice.CalibrationScreen
import pianoanchor.feature.practice.CalibrationViewModel
import pianoanchor.feature.practice.PracticeScreen
import pianoanchor.feature.practice.PracticeViewModel
import pianoanchor.feature.practice.ResultsScreen
import pianoanchor.feature.practice.ResultsState
import pianoanchor.feature.practice.ResultsViewModel
import pianoanchor.feature.settings.SettingsActions
import pianoanchor.feature.settings.SettingsScreen
import pianoanchor.feature.settings.SettingsViewModel
import pianoanchor.feature.songs.SongsActions
import pianoanchor.feature.songs.SongsScreen
import pianoanchor.feature.songs.SongsTarget
import pianoanchor.feature.songs.SongsViewModel
import pianoanchor.feature.today.TodayActions
import pianoanchor.feature.today.TodayScreen
import pianoanchor.feature.today.TodayTarget
import pianoanchor.feature.today.TodayViewModel
import pianoanchor.ui.shell.PianoAnchorTheme
import java.io.IOException
import pianoanchor.engine.lesson.Quiz as ReviewQuiz

private const val FIXTURE_MIME_TYPE = "application/x-ndjson"
private const val FIXTURE_FILE_NAME = "piano-anchor-fixture.jsonl"
private const val KIT_MIME_TYPE = "application/json"
private const val KIT_FILE_NAME = "piano-anchor-kit.json"
private val BLUETOOTH_PERMISSIONS = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The phone sits on the music rest throughout: keep the screen on while the app is in the foreground.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent { PianoAnchorTheme { AppNavigation() } }
    }
}

/**
 * Where the app is: the first-launch onboarding, Today, the pianos (the note monitor), the settings, a lesson,
 * practising or calibrating, or the results.
 */
private sealed interface Destination {
    /** The start screen. */
    data object Today : Destination

    /** The pianos (the note monitor), opened from a rail; back returns to the screen that opened it ([from]). */
    data class Monitor(
        val from: Destination,
    ) : Destination

    /** The Songs screen, opened from the rail; back returns to Today. */
    data object Songs : Destination

    /** The first-launch onboarding, until it is finished. */
    data object Onboarding : Destination

    /** The piano list opened from the onboarding; back returns to the onboarding. */
    data object OnboardingPianos : Destination

    /** The settings, opened from a rail; back returns to the screen that opened them ([from]). */
    data class Settings(
        val from: Destination,
    ) : Destination

    /**
     * Practising [passageId] on [device], starting on [rung] (a card's);
     * back returns to the screen that opened it ([from]): Today, Songs or the monitor. With [spot], it loops those
     * bars of the passage one rung below [rung] (a spot loop); "Play it" then plays the whole passage at [rung].
     * [fromLesson] is the concept whose lesson opened it: Results then offers "Finish lesson".
     */
    data class Practice(
        val device: MidiDevice,
        val passageId: String = Passages.CDE.id,
        val rung: TempoRung = TempoRung.WAIT,
        val from: Destination,
        val mode: RunMode = RunMode.PRACTICE,
        val spot: BarRange? = null,
        val fromLesson: String? = null,
    ) : Destination

    /**
     * The five-step lesson of the course concept [skillId] on [device], opened at [startAt] (null: the saved step);
     * back returns to Today.
     */
    data class Lesson(
        val device: MidiDevice,
        val skillId: String,
        val startAt: LessonStep? = null,
    ) : Destination

    /** The review quizzes on [device], run in order; back returns to Today. */
    data class Quiz(
        val device: MidiDevice,
        val quizzes: List<ReviewQuiz>,
    ) : Destination

    /** The ear screen on [device], running [skills] in order; back returns to Today. */
    data class Ear(
        val device: MidiDevice,
        val skills: List<Skill>,
    ) : Destination

    /** Calibrating [device]'s latency; [returnTo] is where to return to. */
    data class Calibrate(
        val device: MidiDevice,
        val returnTo: Destination,
    ) : Destination

    /**
     * The results of the run saved as [attemptId], played on [rung], which [practice] started; "Try again" returns
     * to it.
     */
    data class Results(
        val attemptId: String,
        val rung: TempoRung,
        val practice: Practice,
    ) : Destination
}

@Composable
private fun AppNavigation() {
    var destination by remember { mutableStateOf<Destination?>(null) }
    OnboardedEffect { onboarded -> destination = if (onboarded) Destination.Today else Destination.Onboarding }
    val back = { destination = Destination.Today }
    when (val current = destination ?: return) {
        Destination.Onboarding, Destination.OnboardingPianos -> {
            OnboardingNavigation(current, go = { destination = it }, onFinished = back)
        }

        Destination.Today -> {
            TodayRoute(
                onPractice = { destination = it },
                onEar = { destination = it },
                onLesson = { destination = it },
                onQuiz = { destination = it },
                onSongs = { destination = Destination.Songs },
                onPianos = { destination = Destination.Monitor(from = current) },
                onSettings = { destination = Destination.Settings(from = current) },
            )
        }

        Destination.Songs -> {
            SongsRoute(
                onPractice = { destination = it },
                onToday = back,
                onPianos = { destination = Destination.Monitor(from = current) },
                onSettings = { destination = Destination.Settings(from = current) },
            )
        }

        is Destination.Monitor -> {
            MonitorRoute(
                onBack = { destination = current.from },
                onPractice = { destination = Destination.Practice(it, from = current) },
                onCalibrate = { destination = Destination.Calibrate(it, returnTo = current) },
            )
        }

        is Destination.Settings -> {
            SettingsRoute(onBack = { destination = current.from })
        }

        is Destination.Practice -> {
            PracticeDestination(current, go = { destination = it })
        }

        is Destination.Ear -> {
            EarRoute(current, onBack = back)
        }

        is Destination.Quiz -> {
            QuizRoute(current, onBack = back)
        }

        is Destination.Lesson -> {
            key(current) { LessonRoute(current, go = { destination = it }) }
        }

        is Destination.Calibrate -> {
            CalibrationRoute(current.device, onLeave = { destination = current.returnTo })
        }

        is Destination.Results -> {
            ResultsRoute(current, go = { destination = it })
        }
    }
}

/** Practice of [current], keyed so "Play it" (the whole passage after a spot loop) builds a new ViewModel. */
@Composable
private fun PracticeDestination(
    current: Destination.Practice,
    go: (Destination) -> Unit,
) {
    key(current) {
        PracticeRoute(
            current,
            onBack = { go(current.from) },
            onFinished = { id, rung -> go(Destination.Results(id, rung, current)) },
            onPlayWhole = { rung -> go(current.copy(rung = rung, spot = null)) },
        )
    }
}

/** The first-launch onboarding and the piano list it opens ([current] is one of the two); [go] navigates. */
@Composable
private fun OnboardingNavigation(
    current: Destination,
    go: (Destination) -> Unit,
    onFinished: () -> Unit,
) {
    if (current == Destination.Onboarding) {
        OnboardingRoute(onPianos = { go(Destination.OnboardingPianos) }, onFinished = onFinished)
    } else {
        MonitorRoute(
            onBack = { go(Destination.Onboarding) },
            // No way out of the onboarding but back: it must be finished first.
            onPractice = null,
            onCalibrate = null,
        )
    }
}

/** A ViewModel store owner that lives as long as the calling route: its ViewModels are cleared when it leaves. */
@Composable
private fun rememberRouteViewModelStoreOwner(): ViewModelStoreOwner {
    val owner = remember { RouteViewModelStoreOwner() }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    return owner
}

/**
 * The practice screen, landscape (the phone sits on the music rest). Its ViewModel lives as long as the route, so
 * leaving discards the run and closes the piano for the monitor.
 */
@Composable
private fun PracticeRoute(
    practice: Destination.Practice,
    onBack: () -> Unit,
    onFinished: (attemptId: String, rung: TempoRung) -> Unit,
    onPlayWhole: (TempoRung) -> Unit,
) {
    val viewModel: PracticeViewModel =
        koinViewModel(viewModelStoreOwner = rememberRouteViewModelStoreOwner()) {
            parametersOf(
                practice.device,
                practice.passageId,
                PracticeStart(practice.rung, practice.mode, practice.spot),
            )
        }
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    // Leave only once the run has closed the piano, so the monitor can reopen it (one reader per input).
    val leave = { then: () -> Unit -> scope.launch { viewModel.close() }.invokeOnCompletion { then() } }
    BackHandler { leave(onBack) }
    LaunchedEffect(state.finishedAttemptId) {
        state.finishedAttemptId?.let { id ->
            val rung = state.rung // the rung the run was played on, read before the screen closes
            leave { onFinished(id, rung) }
        }
    }
    PracticeScreen(
        state,
        onBack = { leave(onBack) },
        onSelectRung = viewModel::selectRung,
        onToggleClick = viewModel::toggleClick,
        onRetrySave = viewModel::retrySave,
        onDiscard = viewModel::discardRun,
        onHint = viewModel::showHint,
        onSelectMode = viewModel::selectMode,
        onPlayWhole = { state.spotLoop?.let { loop -> leave { onPlayWhole(loop.fullRung) } } },
        onKeepLooping = viewModel::keepLooping,
        onListen = viewModel::listen,
        onSkipListening = viewModel::skipListening,
    )
}

/**
 * The ear screen. Its ViewModel lives as long as the route. Leaving (Done, back) closes the piano first, so Today or
 * the monitor can reopen it (one reader per input); skills already finished stay recorded.
 */
@Composable
private fun EarRoute(
    ear: Destination.Ear,
    onBack: () -> Unit,
) {
    val viewModel: EarViewModel =
        koinViewModel(viewModelStoreOwner = rememberRouteViewModelStoreOwner()) { parametersOf(ear.device, ear.skills) }
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val leave = { scope.launch { viewModel.close() }.invokeOnCompletion { onBack() } }
    BackHandler { leave() }
    EarScreen(
        state,
        EarActions(
            onBack = { leave() },
            onReplay = viewModel::replay,
            onAnswer = viewModel::answer,
            onShowMe = viewModel::showMe,
            onNext = viewModel::next,
            onDone = { leave() },
        ),
    )
}

/**
 * The quiz screen, landscape. Its ViewModel lives as long as the route. Leaving (back, Done) closes the piano first, so
 * Today can reopen it (one reader per input); quizzes already finished stay recorded.
 */
@Composable
private fun QuizRoute(
    quiz: Destination.Quiz,
    onBack: () -> Unit,
) {
    val viewModel: QuizViewModel =
        koinViewModel(viewModelStoreOwner = rememberRouteViewModelStoreOwner()) {
            parametersOf(quiz.device, quiz.quizzes)
        }
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val leave = { scope.launch { viewModel.close() }.invokeOnCompletion { onBack() } }
    BackHandler { leave() }
    QuizScreen(state, QuizActions(onBack = { leave() }, onSkip = viewModel::skip, onDone = { leave() }))
}

/**
 * The lesson screen, landscape. Its ViewModel lives as long as the route. Leaving (back, Done, Play it, Next lesson)
 * closes the piano first, so the next screen can open it (one reader per input).
 */
@Composable
private fun LessonRoute(
    lesson: Destination.Lesson,
    go: (Destination) -> Unit,
) {
    val viewModel: LessonViewModel =
        koinViewModel(viewModelStoreOwner = rememberRouteViewModelStoreOwner()) {
            parametersOf(lesson.device, lesson.skillId, lesson.startAt)
        }
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val leave = { to: Destination -> scope.launch { viewModel.close() }.invokeOnCompletion { go(to) } }
    val today = Destination.Today
    BackHandler { leave(today) }
    LessonScreen(
        state,
        LessonActions(
            onBack = { leave(today) },
            onGoTo = viewModel::goTo,
            onNext = viewModel::next,
            onSkip = viewModel::skip,
            onPlayAgain = viewModel::replaySee,
            onPlayIt = {
                state.usePassage?.let { passage ->
                    // Back from practice returns to this lesson's Use step; Results then offers "Finish lesson".
                    val back = Destination.Lesson(lesson.device, lesson.skillId, LessonStep.USE)
                    leave(
                        Destination.Practice(
                            lesson.device,
                            passage.id,
                            TempoRung.WAIT,
                            from = back,
                            fromLesson = lesson.skillId,
                        ),
                    )
                }
            },
            onNextLesson = {
                state.recap?.nextSkillId?.let { leave(Destination.Lesson(lesson.device, it)) }
            },
            onDone = { leave(today) },
        ),
    )
}

/**
 * The calibration screen, landscape like practice. Leaving (Done, Cancel, back) closes the piano first, so the screen
 * it returns to can reopen it (one reader per input).
 */
@Composable
private fun CalibrationRoute(
    device: MidiDevice,
    onLeave: () -> Unit,
) {
    val viewModel: CalibrationViewModel =
        koinViewModel(viewModelStoreOwner = rememberRouteViewModelStoreOwner()) { parametersOf(device) }
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val leave = { scope.launch { viewModel.close() }.invokeOnCompletion { onLeave() } }
    BackHandler { leave() }
    CalibrationScreen(
        state,
        onStart = viewModel::start,
        onRetrySave = viewModel::retrySave,
        onDone = { leave() },
        onCancel = { leave() },
    )
}

/**
 * The results of the run saved as [results], landscape like practice. "Try again" practises on the same piano; "Loop
 * bars" opens a spot loop on those bars at the run's rung; [go] navigates.
 */
@Composable
private fun ResultsRoute(
    results: Destination.Results,
    go: (Destination) -> Unit,
) {
    val viewModel: ResultsViewModel =
        koinViewModel(viewModelStoreOwner = rememberRouteViewModelStoreOwner()) {
            parametersOf(results.attemptId, results.rung)
        }
    val state by viewModel.state.collectAsState()
    val onBack = { go(results.practice.from) }
    val onTryAgain = { go(results.practice) }
    val lesson = results.practice.fromLesson
    val onFinishLesson =
        lesson?.let { skillId -> { go(Destination.Lesson(results.practice.device, skillId, LessonStep.RECAP)) } }
    BackHandler(onBack = onBack)
    ResultsScreen(
        state,
        onBack = onBack,
        onTryAgain = onTryAgain,
        onRetry = viewModel::retry,
        onFinishLesson = onFinishLesson,
        onLoopBars = { spot ->
            (state as? ResultsState.Ready)?.let {
                go(results.practice.copy(rung = it.rung, mode = RunMode.PRACTICE, spot = spot))
            }
        },
    )
}

/**
 * The first-launch onboarding. Its ViewModel lives as long as the route. Leaving for the piano list closes the piano
 * (one reader per input) and leaves the Bluetooth pianos
 * the search connected connected, so the list shows them without a new scan.
 */
@Composable
private fun OnboardingRoute(
    onPianos: () -> Unit,
    onFinished: () -> Unit,
) {
    val viewModel: OnboardingViewModel = koinViewModel(viewModelStoreOwner = rememberRouteViewModelStoreOwner())
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val askBluetooth =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { viewModel.scanBluetooth() }
    // Scan whatever the answer: without permission the adapter reports it and the search goes on over USB.
    LaunchedEffect(viewModel) { askBluetooth.launch(BLUETOOTH_PERMISSIONS) }
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }
    OnboardingScreen(
        state,
        OnboardingActions(
            onOpenPianoList = { scope.launch { viewModel.handOverToPianoList() }.invokeOnCompletion { onPianos() } },
            onReplay = viewModel::replay,
            onContinue = viewModel::continueToStartPoint,
            onStart = viewModel::start,
        ),
    )
}

/** The settings screen; its ViewModel lives as long as the route. */
@Composable
private fun SettingsRoute(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = koinViewModel(viewModelStoreOwner = rememberRouteViewModelStoreOwner())
    val state by viewModel.state.collectAsState()
    BackHandler(onBack = onBack)
    SettingsScreen(
        state,
        SettingsActions(
            onBack = onBack,
            onDailyBudget = viewModel::setDailyBudget,
            onWeeklyGoalStep = viewModel::changeWeeklyGoalDays,
            onClickOn = viewModel::setClickOn,
            onStrictness = viewModel::setStrictness,
            onUpdateCheckOn = viewModel::setUpdateCheckOn,
            onDismissError = viewModel::dismissError,
        ),
    )
}

/** Today; its ViewModel lives as long as the route, so coming back from a run loads the plan afresh. */
@Composable
private fun TodayRoute(
    onPractice: (Destination.Practice) -> Unit,
    onEar: (Destination.Ear) -> Unit,
    onLesson: (Destination.Lesson) -> Unit,
    onQuiz: (Destination.Quiz) -> Unit,
    onSongs: () -> Unit,
    onPianos: () -> Unit,
    onSettings: () -> Unit,
) {
    val viewModel: TodayViewModel = koinViewModel(viewModelStoreOwner = rememberRouteViewModelStoreOwner())
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    TodayScreen(
        state,
        TodayActions(
            onStart = { card ->
                when (val target = viewModel.target(card)) {
                    is TodayTarget.Practice -> {
                        onPractice(
                            Destination.Practice(
                                target.device,
                                target.passageId,
                                target.rung,
                                Destination.Today,
                                target.mode,
                            ),
                        )
                    }

                    TodayTarget.Pianos -> {
                        onPianos()
                    }

                    is TodayTarget.Ear -> {
                        onEar(Destination.Ear(target.device, target.skills))
                    }

                    is TodayTarget.Quizzes -> {
                        onQuiz(Destination.Quiz(target.device, target.quizzes))
                    }

                    is TodayTarget.Lesson -> {
                        onLesson(Destination.Lesson(target.device, target.skillId))
                    }
                }
            },
            onSongs = onSongs,
            onPianos = onPianos,
            onSettings = onSettings,
            onRetry = viewModel::refresh,
            onSeeRelease = { release -> openInBrowser(context, release.pageUrl) },
            onDismissUpdate = viewModel::dismissUpdate,
        ),
    )
}

/** Songs; its ViewModel lives as long as the route, so coming back from a run loads the pieces afresh. */
@Composable
private fun SongsRoute(
    onPractice: (Destination.Practice) -> Unit,
    onToday: () -> Unit,
    onPianos: () -> Unit,
    onSettings: () -> Unit,
) {
    val viewModel: SongsViewModel = koinViewModel(viewModelStoreOwner = rememberRouteViewModelStoreOwner())
    val state by viewModel.state.collectAsState()
    BackHandler(onBack = onToday)
    SongsScreen(
        state,
        SongsActions(
            onOpen = { piece ->
                when (val target = viewModel.target(piece)) {
                    is SongsTarget.Practice -> {
                        onPractice(
                            Destination.Practice(target.device, target.passageId, target.rung, Destination.Songs),
                        )
                    }

                    SongsTarget.Pianos -> {
                        onPianos()
                    }
                }
            },
            onToday = onToday,
            onPianos = onPianos,
            onSettings = onSettings,
            onRetry = viewModel::refresh,
        ),
    )
}

/** A ViewModel store scoped to one route: cleared when the route leaves the composition. */
private class RouteViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/** Platform glue for the note monitor: Bluetooth permission, settings intents and the SAF export. */
@Composable
private fun MonitorRoute(
    onBack: () -> Unit,
    onPractice: ((MidiDevice) -> Unit)?,
    onCalibrate: ((MidiDevice) -> Unit)?,
    viewModel: MonitorViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    // Coming back from practice: reopen the piano the monitor closed for it.
    LaunchedEffect(viewModel) { viewModel.setDeviceOpen(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Today's cards open Practice on this piano: close the monitor's reader first (one reader per input).
    BackHandler { scope.handOverPiano(viewModel, onBack) }
    // Scan whatever the answer: without permission the adapter reports it and the screen offers the settings.
    val askBluetooth =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { viewModel.scanBluetooth() }
    val export = rememberSaveLauncher(FIXTURE_MIME_TYPE) { viewModel.exportJsonl() }
    val saveKit = rememberSaveLauncher(KIT_MIME_TYPE) { viewModel.exportKit() }
    MonitorScreen(
        state = state,
        actions =
            MonitorActions(
                onSelect = viewModel::select,
                onScan = { askBluetooth.launch(BLUETOOTH_PERMISSIONS) },
                onConnect = viewModel::connect,
                onForget = viewModel::forget,
                onStartRecording = viewModel::startRecording,
                onStopRecording = viewModel::stopRecording,
                onExport = { export.launch(FIXTURE_FILE_NAME) },
                onSaveKit = { saveKit.launch(KIT_FILE_NAME) },
                onOpenSettings = {
                    val appDetails = Uri.fromParts("package", context.packageName, null)
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appDetails))
                },
                onEnableBluetooth = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                onDismissError = viewModel::dismissError,
                onBack = { scope.handOverPiano(viewModel, onBack) },
                // An input has one reader: the next screen opens the piano once the monitor has closed it.
                onPractice = onPractice?.let { go -> { device -> scope.handOverPiano(viewModel) { go(device) } } },
                onCalibrate = onCalibrate?.let { go -> { device -> scope.handOverPiano(viewModel) { go(device) } } },
            ),
    )
}

/** Closes the monitor's reader of the piano, then runs [next], which opens it elsewhere (one reader per input). */
private fun CoroutineScope.handOverPiano(
    monitor: MonitorViewModel,
    next: () -> Unit,
) {
    launch {
        monitor.setDeviceOpen(false)
        next()
    }
}

/** A "save as" launcher: writes [text] (null: nothing to write) to the file the user picks; toasts on failure. */
@Composable
private fun rememberSaveLauncher(
    mimeType: String,
    text: () -> String?,
): ManagedActivityResultLauncher<String, Uri?> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val written =
                withContext(Dispatchers.IO) {
                    val content = text() ?: return@withContext false
                    try {
                        context.contentResolver
                            .openOutputStream(uri)
                            ?.use { it.write(content.encodeToByteArray()) } != null
                    } catch (_: IOException) {
                        false
                    }
                }
            if (!written) Toast.makeText(context, R.string.export_failed, Toast.LENGTH_LONG).show()
        }
    }
}
