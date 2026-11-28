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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import pianoanchor.core.model.Band
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
import pianoanchor.feature.lesson.LessonExit
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
import pianoanchor.feature.progress.ProgressActions
import pianoanchor.feature.progress.ProgressScreen
import pianoanchor.feature.progress.ProgressViewModel
import pianoanchor.feature.settings.SettingsActions
import pianoanchor.feature.settings.SettingsScreen
import pianoanchor.feature.settings.SettingsViewModel
import pianoanchor.feature.songs.SongsActions
import pianoanchor.feature.songs.SongsScreen
import pianoanchor.feature.songs.SongsTarget
import pianoanchor.feature.songs.SongsViewModel
import pianoanchor.feature.today.LeaveSessionDialog
import pianoanchor.feature.today.SessionFlowActions
import pianoanchor.feature.today.SessionFlowScreen
import pianoanchor.feature.today.SessionFlowViewModel
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

    /**
     * The one-tap session between its steps: the card that counts down to the next step, then the recap. [stepDone] is
     * true when a step just finished and handed control back.
     */
    data class Session(
        val stepDone: Boolean,
    ) : Destination

    /** The Songs screen, opened from the rail; back returns to Today. */
    data object Songs : Destination

    /** The Progress tab, opened from the rail; back returns to Today. */
    data object Progress : Destination

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
     * [fromLesson] is the concept whose lesson opened it: Results then offers "Finish lesson". A [placement] run is a
     * piece of the onboarding's placement test, hosted in the onboarding (no Results). A [flowStep] is a step of the
     * one-tap session: finishing it opens Results with "Continue" back to the session (a [backing] play-along step
     * returns to the session at once). [backing] is the session's play-along
     * step: chords and click, on a timed rung.
     */
    data class Practice(
        val device: MidiDevice,
        val passageId: String = Passages.CDE.id,
        val rung: TempoRung = TempoRung.WAIT,
        val from: Destination,
        val mode: RunMode = RunMode.PRACTICE,
        val spot: BarRange? = null,
        val fromLesson: String? = null,
        val placement: Boolean = false,
        val flowStep: Boolean = false,
        val backing: Boolean = false,
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
    // The session flow's ViewModel outlives the step screens it opens, so it lives as long as the navigation.
    val flow: SessionFlowViewModel = koinViewModel(viewModelStoreOwner = remember { RouteViewModelStoreOwner() })
    var flowActive by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var pendingLeave by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Where a step returns to: the session while it runs, Today once the learner chose to leave it.
    val home = { if (flowActive && !leaving) Destination.Session(stepDone = true) else Destination.Today }
    val back = { destination = home() }
    val askLeave: (() -> Unit) -> Unit = { proceed -> if (flowActive) pendingLeave = proceed else proceed() }
    LaunchedEffect(destination) {
        if (destination == Destination.Today && flowActive) {
            flow.exit()
            flowActive = false
            leaving = false
        }
    }
    pendingLeave?.let { proceed ->
        LeaveSessionDialog(
            onKeepGoing = { pendingLeave = null },
            onLeave = {
                leaving = true
                pendingLeave = null
                proceed()
            },
        )
    }
    val current = destination ?: return
    CompositionLocalProvider(LocalLeaveGuard provides askLeave) {
        Navigation(current, flow, home, back, go = { destination = it }) {
            flowActive = true
            leaving = false
        }
    }
}

/** Asks "Leave today's session?" before running the action when the session flow is on; else runs it at once. */
private val LocalLeaveGuard = staticCompositionLocalOf<(() -> Unit) -> Unit> { { it() } }

@Composable
private fun Navigation(
    current: Destination,
    flow: SessionFlowViewModel,
    home: () -> Destination,
    back: () -> Unit,
    go: (Destination) -> Unit,
    onFlowStarted: () -> Unit,
) {
    when (current) {
        Destination.Onboarding, Destination.OnboardingPianos -> {
            OnboardingNavigation(current, go = { go(it) }, onFinished = back)
        }

        Destination.Today -> {
            TodayRoute(
                flow = flow,
                toSession = {
                    onFlowStarted()
                    go(Destination.Session(stepDone = false))
                },
                toSongs = { go(Destination.Songs) },
                toProgress = { go(Destination.Progress) },
                toPianos = { go(Destination.Monitor(from = current)) },
                toSettings = { go(Destination.Settings(from = current)) },
            )
        }

        Destination.Songs, Destination.Progress -> {
            RailTab(current, back, go)
        }

        is Destination.Monitor -> {
            MonitorRoute(
                onBack = { go(current.from) },
                onPractice = { go(Destination.Practice(it, from = current)) },
                onCalibrate = { go(Destination.Calibrate(it, returnTo = current)) },
            )
        }

        is Destination.Settings -> {
            SettingsRoute(onBack = { go(current.from) })
        }

        is Destination.Session -> {
            SessionRoute(current, flow, go = { go(it) })
        }

        is Destination.Practice -> {
            PracticeDestination(current, home, go = { go(it) })
        }

        is Destination.Ear -> {
            EarRoute(current, onBack = back)
        }

        is Destination.Quiz -> {
            QuizRoute(current, onBack = back)
        }

        is Destination.Lesson -> {
            key(current) { LessonRoute(current, home, go = { go(it) }) }
        }

        is Destination.Calibrate -> {
            CalibrationRoute(current.device, onLeave = { go(current.returnTo) })
        }

        is Destination.Results -> {
            ResultsRoute(current, home, go = { go(it) })
        }
    }
}

/** The Songs and Progress tabs, which lead to each other, to Today (back), to the pianos and to the settings. */
@Composable
private fun RailTab(
    current: Destination,
    back: () -> Unit,
    go: (Destination) -> Unit,
) {
    val toProgress = { go(Destination.Progress) }
    val toPianos = { go(Destination.Monitor(from = current)) }
    val toSettings = { go(Destination.Settings(from = current)) }
    if (current == Destination.Songs) {
        SongsRoute(
            onPractice = { go(it) },
            onToday = back,
            onProgress = toProgress,
            onPianos = toPianos,
            onSettings = toSettings,
        )
    } else {
        ProgressRoute(
            onToday = back,
            onSongs = { go(Destination.Songs) },
            onPianos = toPianos,
            onSettings = toSettings,
        )
    }
}

/** Practice of [current], keyed so "Play it" (the whole passage after a spot loop) builds a new ViewModel. */
@Composable
private fun PracticeDestination(
    current: Destination.Practice,
    home: () -> Destination,
    go: (Destination) -> Unit,
) {
    key(current) {
        PracticeRoute(
            current,
            onBack = { go(current.from) },
            onFinished = { id, rung, _ ->
                go(if (current.flowStep && current.backing) home() else Destination.Results(id, rung, current))
            },
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
    onFinished: (attemptId: String, rung: TempoRung, band: Band?) -> Unit,
    onPlayWhole: (TempoRung) -> Unit,
    stopRequested: Boolean = false,
) {
    val viewModel: PracticeViewModel =
        koinViewModel(viewModelStoreOwner = rememberRouteViewModelStoreOwner()) {
            parametersOf(
                practice.device,
                practice.passageId,
                PracticeStart(
                    practice.rung,
                    practice.mode,
                    practice.spot,
                    practice.placement,
                    practice.fromLesson != null,
                    practice.backing,
                ),
            )
        }
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    // Leave only once the run has closed the piano, so the monitor can reopen it (one reader per input).
    val leave = { then: () -> Unit -> scope.launch { viewModel.close() }.invokeOnCompletion { then() } }
    val askLeave = LocalLeaveGuard.current
    BackHandler { askLeave { leave(onBack) } }
    // The onboarding's "Stop here": leave like back does, closing the piano first.
    LaunchedEffect(stopRequested) { if (stopRequested) leave(onBack) }
    LaunchedEffect(state.finishedAttemptId) {
        state.finishedAttemptId?.let { id ->
            val rung = state.rung // the rung the run was played on, read before the screen closes
            val band = state.finishedBand
            leave { onFinished(id, rung, band) }
        }
    }
    PracticeScreen(
        state,
        onBack = { askLeave { leave(onBack) } },
        onSelectRung = viewModel::selectRung,
        onToggleClick = viewModel::toggleClick,
        onRetrySave = viewModel::retrySave,
        onDiscard = viewModel::discardRun,
        onHint = viewModel::showHint,
        onSelectMode = viewModel::selectMode,
        onPlayWhole = { state.spotLoop?.let { loop -> leave { onPlayWhole(loop.fullRung) } } },
        onKeepLooping = viewModel::keepLooping,
        onSlower = viewModel::slowerRung,
        onKeepTrying = viewModel::keepTrying,
        onToggleBacking = viewModel::toggleBacking,
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
    val askLeave = LocalLeaveGuard.current
    BackHandler { askLeave { leave() } }
    EarScreen(
        state,
        EarActions(
            onBack = { askLeave { leave() } },
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
    val askLeave = LocalLeaveGuard.current
    BackHandler { askLeave { leave() } }
    // A tap of the sustain pedal in the summary is "Done".
    LaunchedEffect(viewModel) { viewModel.pedalDone.collect { leave() } }
    QuizScreen(state, QuizActions(onBack = { askLeave { leave() } }, onSkip = viewModel::skip, onDone = { leave() }))
}

/**
 * The lesson screen, landscape. Its ViewModel lives as long as the route. Leaving (back, Done, Play it, Next lesson)
 * closes the piano first, so the next screen can open it (one reader per input).
 */
@Composable
private fun LessonRoute(
    lesson: Destination.Lesson,
    home: () -> Destination,
    go: (Destination) -> Unit,
) {
    val viewModel: LessonViewModel =
        koinViewModel(viewModelStoreOwner = rememberRouteViewModelStoreOwner()) {
            parametersOf(lesson.device, lesson.skillId, lesson.startAt)
        }
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val leave = { to: Destination -> scope.launch { viewModel.close() }.invokeOnCompletion { go(to) } }
    val askLeave = LocalLeaveGuard.current
    BackHandler { askLeave { leave(home()) } }
    val playIt = {
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
    }
    val nextLesson = { state.recap?.nextSkillId?.let { leave(Destination.Lesson(lesson.device, it)) } }
    // What a pedal tap or the Use step's timer asks for, as the buttons do.
    LaunchedEffect(viewModel) {
        viewModel.exits.collect { exit ->
            when (exit) {
                LessonExit.PLAY_IT -> playIt()
                LessonExit.NEXT_LESSON -> nextLesson()
                LessonExit.DONE -> leave(home())
            }
        }
    }
    LessonScreen(
        state,
        LessonActions(
            onBack = { askLeave { leave(home()) } },
            onGoTo = viewModel::goTo,
            onNext = viewModel::next,
            onSkip = viewModel::skip,
            onPlayAgain = viewModel::replaySee,
            onPlayIt = { playIt() },
            onNextLesson = { nextLesson() },
            onDone = { leave(home()) },
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
    home: () -> Destination,
    go: (Destination) -> Unit,
) {
    val viewModel: ResultsViewModel =
        koinViewModel(viewModelStoreOwner = rememberRouteViewModelStoreOwner()) {
            parametersOf(results.attemptId, results.rung, results.practice.device)
        }
    val state by viewModel.state.collectAsState()
    val pedalHint by viewModel.pedalHint.collectAsState()
    val scope = rememberCoroutineScope()
    // Results read the piano for the pedal: leave only once it is closed, so the next screen can open it.
    val leave: (Destination) -> Unit = { to ->
        scope.launch { viewModel.releasePiano() }.invokeOnCompletion { go(to) }
    }
    val askLeave = LocalLeaveGuard.current
    val inSession = results.practice.flowStep
    // Back from a session step asks "Leave today's session?" like the step itself does.
    val onBack = {
        val toFrom = { leave(results.practice.from) }
        if (inSession) askLeave(toFrom) else toFrom()
    }
    val onTryAgain = { leave(results.practice) }
    val lesson = results.practice.fromLesson
    val onFinishLesson =
        lesson?.let { skillId -> { leave(Destination.Lesson(results.practice.device, skillId, LessonStep.RECAP)) } }
    // The session marks the step finished when it gets control back.
    val onContinue = if (inSession) ({ leave(home()) }) else null
    BackHandler(onBack = onBack)
    // A tap of the sustain pedal does what the primary button does.
    LaunchedEffect(viewModel) { viewModel.pedalContinues.collect { (onContinue ?: onFinishLesson ?: onTryAgain)() } }
    ResultsScreen(
        state,
        onBack = onBack,
        onTryAgain = onTryAgain,
        onRetry = viewModel::retry,
        onFinishLesson = onFinishLesson,
        onContinue = onContinue,
        pedalHint = pedalHint,
        onLoopBars = { spot ->
            (state as? ResultsState.Ready)?.let {
                leave(results.practice.copy(rung = it.rung, mode = RunMode.PRACTICE, spot = spot))
            }
        },
    )
}

/**
 * The first-launch onboarding. Its ViewModel lives as long as the route. Leaving for the piano list closes the piano
 * (one reader per input) and leaves the Bluetooth pianos
 * the search connected connected, so the list shows them without a new scan. The pieces of the placement test run in
 * the screen's slot: onboarding has closed the piano for them, and each run closes it again before it reports. "Stop
 * here" is passed to the run, which closes the piano and then ends the test, so it never leaves composition with the
 * piano still open.
 */
@Composable
private fun OnboardingRoute(
    onPianos: () -> Unit,
    onFinished: () -> Unit,
) {
    val viewModel: OnboardingViewModel = koinViewModel(viewModelStoreOwner = rememberRouteViewModelStoreOwner())
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    // "Stop here" goes through the hosted run, which closes the piano before it reports (one reader per input).
    var stopRequested by remember(state.piece?.number) { mutableStateOf(false) }
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
            onTestMe = viewModel::testMe,
            onStartAtBeginning = viewModel::startFromBeginning,
            onStopHere = { if (state.device == null) viewModel.stopHere() else stopRequested = true },
            onNextPiece = viewModel::continueTest,
        ),
    ) { piece ->
        // The placement test's runs are hosted here, in the onboarding's slot, on the piano the learner picked.
        state.device?.let { device ->
            key(piece.number) {
                PracticeRoute(
                    Destination.Practice(device, piece.passageId, from = Destination.Onboarding, placement = true),
                    onBack = viewModel::stopHere,
                    onFinished = { _, _, band -> viewModel.placementRunFinished(band ?: Band.KEEP_PRACTISING) },
                    onPlayWhole = {},
                    stopRequested = stopRequested,
                )
            }
        }
    }
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
            onPedalControlOn = viewModel::setPedalControlOn,
            onNoteNames = viewModel::setNoteNames,
            onStrictness = viewModel::setStrictness,
            onUpdateCheckOn = viewModel::setUpdateCheckOn,
            onDismissError = viewModel::dismissError,
        ),
    )
}

/** Today; its ViewModel lives as long as the route, so coming back from a run loads the plan afresh. */
@Composable
private fun TodayRoute(
    flow: SessionFlowViewModel,
    toSession: () -> Unit,
    toSongs: () -> Unit,
    toProgress: () -> Unit,
    toPianos: () -> Unit,
    toSettings: () -> Unit,
) {
    val viewModel: TodayViewModel = koinViewModel(viewModelStoreOwner = rememberRouteViewModelStoreOwner())
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Today reads the piano for the pedal: leave only once it is closed, so the next screen can open it (one reader).
    val release: (() -> Unit) -> Unit = { then ->
        if (viewModel.beginLeaving()) scope.launch { viewModel.releasePiano() }.invokeOnCompletion { then() }
    }
    val onSongs = { release { toSongs() } }
    val onProgress = { release { toProgress() } }
    val onPianos = { release { toPianos() } }
    val onSettings = { release { toSettings() } }
    // Start runs the whole session (less the steps switched off); with no piano it opens Pianos.
    val startSession = { skipped: Set<Int> ->
        val session = state.session
        if (state.piano == null || session == null) {
            onPianos()
        } else {
            release {
                flow.start(session, skipped)
                toSession()
            }
        }
    }
    // A tap of the sustain pedal starts the session, as its button does.
    LaunchedEffect(viewModel) { viewModel.pedalStarts.collect { startSession(emptySet()) } }
    TodayScreen(
        state,
        TodayActions(
            onStartSession = startSession,
            onSongs = onSongs,
            onProgress = onProgress,
            onPianos = onPianos,
            onSettings = onSettings,
            onRetry = viewModel::refresh,
            onSeeRelease = { release -> openInBrowser(context, release.pageUrl) },
            onDismissUpdate = viewModel::dismissUpdate,
            onPauseWeek = viewModel::pauseWeek,
        ),
    )
}

/**
 * The session between its steps. Each step the flow opens is a destination of its own; this route shows the card
 * between steps and the recap. Leaving (back, Done) ends the flow; finished steps stay done.
 */
@Composable
private fun SessionRoute(
    session: Destination.Session,
    flow: SessionFlowViewModel,
    go: (Destination) -> Unit,
) {
    val state by flow.state.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(session) { if (session.stepDone) flow.stepFinished() }
    LaunchedEffect(flow) {
        flow.opens.collect { target ->
            // The flow closed its piano reader before it opened the step, so the step's screen can read the piano.
            when (target) {
                is TodayTarget.Practice -> {
                    go(
                        Destination.Practice(
                            target.device,
                            target.passageId,
                            target.rung,
                            Destination.Today,
                            target.mode,
                            flowStep = true,
                            backing = target.backing,
                        ),
                    )
                }

                is TodayTarget.SightReading -> {
                    go(Destination.Practice(target.device, target.passageId, from = Destination.Today, flowStep = true))
                }

                is TodayTarget.Ear -> {
                    go(Destination.Ear(target.device, target.skills))
                }

                is TodayTarget.Quizzes -> {
                    go(Destination.Quiz(target.device, target.quizzes))
                }

                is TodayTarget.Lesson -> {
                    go(Destination.Lesson(target.device, target.skillId))
                }

                TodayTarget.Pianos -> {
                    go(Destination.Monitor(from = Destination.Today))
                }
            }
        }
    }
    val leave = { scope.launch { flow.releasePiano() }.invokeOnCompletion { go(Destination.Today) } }
    BackHandler { leave() }
    SessionFlowScreen(
        state,
        SessionFlowActions(
            onPause = flow::pauseCountdown,
            onResume = flow::resumeCountdown,
            onSkip = flow::skipStep,
            onGoNow = flow::goNow,
            onDone = { leave() },
        ),
    )
}

/** Songs; its ViewModel lives as long as the route, so coming back from a run loads the pieces afresh. */
@Composable
private fun SongsRoute(
    onPractice: (Destination.Practice) -> Unit,
    onToday: () -> Unit,
    onProgress: () -> Unit,
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
            onProgress = onProgress,
            onPianos = onPianos,
            onSettings = onSettings,
            onRetry = viewModel::refresh,
        ),
    )
}

/**
 * Progress; its ViewModel lives as long as the route, so coming back loads the week afresh, and leaving the tab stops
 * the take that sounds.
 */
@Composable
private fun ProgressRoute(
    onToday: () -> Unit,
    onSongs: () -> Unit,
    onPianos: () -> Unit,
    onSettings: () -> Unit,
) {
    val viewModel: ProgressViewModel = koinViewModel(viewModelStoreOwner = rememberRouteViewModelStoreOwner())
    val state by viewModel.state.collectAsState()
    DisposableEffect(viewModel) { onDispose { viewModel.stop() } }
    BackHandler(onBack = onToday)
    ProgressScreen(
        state,
        ProgressActions(
            onToday = onToday,
            onSongs = onSongs,
            onPianos = onPianos,
            onSettings = onSettings,
            onPlay = viewModel::play,
            onStop = viewModel::stop,
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
