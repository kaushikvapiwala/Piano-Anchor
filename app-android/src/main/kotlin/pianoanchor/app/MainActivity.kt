@file:Suppress("TooManyFunctions") // One small route composable per screen of the app.

package pianoanchor.app

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
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
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.Skill
import pianoanchor.engine.content.Passages
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.feature.ear.EarActions
import pianoanchor.feature.ear.EarScreen
import pianoanchor.feature.ear.EarViewModel
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

private const val FIXTURE_MIME_TYPE = "application/x-ndjson"
private const val FIXTURE_FILE_NAME = "piano-anchor-fixture.jsonl"
private const val KIT_MIME_TYPE = "application/json"
private const val KIT_FILE_NAME = "piano-anchor-kit.json"
private val BLUETOOTH_PERMISSIONS = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PianoAnchorTheme { AppNavigation() } }
    }
}

/**
 * Where the app is: the first-launch onboarding, Today, the pianos (the note monitor), the settings, practising or
 * calibrating, or the results.
 */
private sealed interface Destination {
    /** The start screen. */
    data object Today : Destination

    data object Monitor : Destination

    /** The Songs screen, opened from the rail; back returns to Today. */
    data object Songs : Destination

    /** The first-launch onboarding, until it is finished. */
    data object Onboarding : Destination

    /** The piano list opened from the onboarding; back returns to the onboarding. */
    data object OnboardingPianos : Destination

    /** The settings, opened from Today's rail; back returns to Today. */
    data object Settings : Destination

    /**
     * Practising [passageId] on [device], starting on [rung] (a card's, or the one picked before a calibration);
     * back returns to the screen that opened it ([from]): Today, Songs or the monitor.
     */
    data class Practice(
        val device: MidiDevice,
        val passageId: String = Passages.CDE.id,
        val rung: TempoRung = TempoRung.WAIT,
        val from: Destination = Monitor,
    ) : Destination

    /** The ear screen on [device], running [skills] in order; back returns to Today. */
    data class Ear(
        val device: MidiDevice,
        val skills: List<Skill>,
    ) : Destination

    /** Calibrating [device]'s latency; [practice] is the Practice to return to, null for the monitor. */
    data class Calibrate(
        val device: MidiDevice,
        val practice: Practice?,
    ) : Destination

    /** The results of the run saved as [attemptId], which [practice] started; "Try again" returns to it. */
    data class Results(
        val attemptId: String,
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
                onSongs = { destination = Destination.Songs },
                onPianos = { destination = Destination.Monitor },
                onSettings = { destination = Destination.Settings },
            )
        }

        Destination.Songs -> {
            SongsRoute(
                onPractice = { destination = it },
                onToday = back,
                onPianos = { destination = Destination.Monitor },
                onSettings = { destination = Destination.Settings },
            )
        }

        Destination.Monitor -> {
            MonitorRoute(
                onBack = back,
                onPractice = { destination = Destination.Practice(it) },
                onCalibrate = { destination = Destination.Calibrate(it, practice = null) },
            )
        }

        Destination.Settings -> {
            SettingsRoute(onBack = back)
        }

        is Destination.Practice -> {
            PracticeRoute(
                current,
                onBack = { destination = current.from },
                onFinished = { destination = Destination.Results(it, current) },
                onCalibrate = { destination = Destination.Calibrate(current.device, current.copy(rung = it)) },
            )
        }

        is Destination.Ear -> {
            EarRoute(current, onBack = back)
        }

        is Destination.Calibrate -> {
            CalibrationRoute(current.device, onLeave = { destination = current.practice ?: Destination.Monitor })
        }

        is Destination.Results -> {
            ResultsRoute(
                current.attemptId,
                onBack = { destination = current.practice.from },
                onTryAgain = { destination = current.practice },
            )
        }
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

/** Landscape while in the composition (the phone sits on the music rest), back to the system's choice after. */
@Composable
private fun LandscapeEffect() {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
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
    onFinished: (attemptId: String) -> Unit,
    onCalibrate: (TempoRung) -> Unit,
) {
    LandscapeEffect()
    val viewModel: PracticeViewModel =
        koinViewModel(viewModelStoreOwner = rememberRouteViewModelStoreOwner()) {
            parametersOf(practice.device, practice.passageId, practice.rung)
        }
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    // Leave only once the run has closed the piano, so the monitor can reopen it (one reader per input).
    val leave = { then: () -> Unit -> scope.launch { viewModel.close() }.invokeOnCompletion { then() } }
    BackHandler { leave(onBack) }
    LaunchedEffect(state.finishedAttemptId) { state.finishedAttemptId?.let { id -> leave { onFinished(id) } } }
    PracticeScreen(
        state,
        onBack = { leave(onBack) },
        onSelectRung = viewModel::selectRung,
        onToggleClick = viewModel::toggleClick,
        onRetrySave = viewModel::retrySave,
        onDiscard = viewModel::discardRun,
        onCalibrate = { leave { onCalibrate(state.rung) } },
        onSkipCalibration = viewModel::skipCalibration,
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
 * The calibration screen, landscape like practice. Leaving (Done, Cancel, back) closes the piano first, so the screen
 * it returns to can reopen it (one reader per input).
 */
@Composable
private fun CalibrationRoute(
    device: MidiDevice,
    onLeave: () -> Unit,
) {
    LandscapeEffect()
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

/** The results of the run saved as [attemptId], landscape like practice. "Try again" practises on the same piano. */
@Composable
private fun ResultsRoute(
    attemptId: String,
    onBack: () -> Unit,
    onTryAgain: () -> Unit,
) {
    LandscapeEffect()
    val viewModel: ResultsViewModel =
        koinViewModel(viewModelStoreOwner = rememberRouteViewModelStoreOwner()) { parametersOf(attemptId) }
    val state by viewModel.state.collectAsState()
    BackHandler(onBack = onBack)
    ResultsScreen(state, onBack = onBack, onTryAgain = onTryAgain, onRetry = viewModel::retry)
}

/**
 * The first-launch onboarding. Its ViewModel lives as long as the route. The calibration step hosts the calibration
 * screen on the connected piano (the onboarding has closed it first); Done or leaving it moves on, after closing the
 * piano again. Leaving for the piano list closes the piano too (one reader per input).
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
            onOpenPianoList = { scope.launch { viewModel.close() }.invokeOnCompletion { onPianos() } },
            onReplay = viewModel::replay,
            onContinue = viewModel::continueToCalibration,
            onStart = viewModel::start,
        ),
        calibration = { device ->
            val calibration: CalibrationViewModel =
                koinViewModel(viewModelStoreOwner = rememberRouteViewModelStoreOwner()) { parametersOf(device) }
            val calibrationState by calibration.state.collectAsState()
            val leave = { scope.launch { calibration.close() }.invokeOnCompletion { viewModel.calibrationFinished() } }
            BackHandler { leave() }
            CalibrationScreen(
                calibrationState,
                onStart = calibration::start,
                onRetrySave = calibration::retrySave,
                onDone = { leave() },
                onCancel = { leave() },
            )
        },
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
                            Destination.Practice(target.device, target.passageId, target.rung, Destination.Today),
                        )
                    }

                    TodayTarget.Pianos -> {
                        onPianos()
                    }

                    is TodayTarget.Ear -> {
                        // Only the Ear card yields this target.
                        onEar(Destination.Ear(target.device, target.skills))
                    }
                }
            },
            onStartEar = { card ->
                when (val target = viewModel.target(card)) {
                    is TodayTarget.Ear -> {
                        onEar(Destination.Ear(target.device, target.skills))
                    }

                    TodayTarget.Pianos -> {
                        onPianos()
                    }

                    is TodayTarget.Practice -> {
                        // Only passage cards yield this target.
                        onPianos()
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

                    null -> {
                        // A locked piece does nothing.
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
