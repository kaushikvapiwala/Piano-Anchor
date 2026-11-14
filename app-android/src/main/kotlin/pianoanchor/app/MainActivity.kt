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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import pianoanchor.core.model.MidiDevice
import pianoanchor.feature.monitor.MonitorActions
import pianoanchor.feature.monitor.MonitorScreen
import pianoanchor.feature.monitor.MonitorViewModel
import pianoanchor.feature.practice.PracticeScreen
import pianoanchor.feature.practice.PracticeViewModel
import pianoanchor.feature.practice.ResultsScreen
import pianoanchor.feature.practice.ResultsViewModel
import pianoanchor.ui.shell.PianoAnchorTheme
import java.io.IOException

private const val FIXTURE_MIME_TYPE = "application/x-ndjson"
private const val FIXTURE_FILE_NAME = "piano-anchor-fixture.jsonl"
private val BLUETOOTH_PERMISSIONS = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PianoAnchorTheme { AppNavigation() } }
    }
}

/** Where the app is: the monitor, practising on a piano, or the results of a finished run on it. */
private sealed interface Destination {
    data object Monitor : Destination

    data class Practice(
        val device: MidiDevice,
    ) : Destination

    data class Results(
        val device: MidiDevice,
        val attemptId: String,
    ) : Destination
}

@Composable
private fun AppNavigation() {
    var destination by remember { mutableStateOf<Destination>(Destination.Monitor) }
    val back = { destination = Destination.Monitor }
    when (val current = destination) {
        Destination.Monitor -> {
            MonitorRoute(onPractice = { destination = Destination.Practice(it) })
        }

        is Destination.Practice -> {
            PracticeRoute(
                current.device,
                onBack = back,
                onFinished = { destination = Destination.Results(current.device, it) },
            )
        }

        is Destination.Results -> {
            ResultsRoute(
                current.attemptId,
                onBack = back,
                onTryAgain = { destination = Destination.Practice(current.device) },
            )
        }
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
    device: MidiDevice,
    onBack: () -> Unit,
    onFinished: (attemptId: String) -> Unit,
) {
    LandscapeEffect()
    val viewModel: PracticeViewModel =
        koinViewModel(viewModelStoreOwner = rememberRouteViewModelStoreOwner()) { parametersOf(device) }
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    // Leave only once the run has closed the piano, so the monitor can reopen it (one reader per input).
    val leave = { then: () -> Unit -> scope.launch { viewModel.close() }.invokeOnCompletion { then() } }
    BackHandler { leave(onBack) }
    LaunchedEffect(state.finishedAttemptId) { state.finishedAttemptId?.let { id -> leave { onFinished(id) } } }
    PracticeScreen(
        state,
        onBack = { leave(onBack) },
        onRetrySave = viewModel::retrySave,
        onDiscard = viewModel::discardRun,
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

/** A ViewModel store scoped to one route: cleared when the route leaves the composition. */
private class RouteViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/** Platform glue for the note monitor: Bluetooth permission, settings intents and the SAF export. */
@Composable
private fun MonitorRoute(
    onPractice: (MidiDevice) -> Unit,
    viewModel: MonitorViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    // Coming back from practice: reopen the piano the monitor closed for it.
    LaunchedEffect(viewModel) { viewModel.setDeviceOpen(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Scan whatever the answer: without permission the adapter reports it and the screen offers the settings.
    val askBluetooth =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { viewModel.scanBluetooth() }
    val export =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FIXTURE_MIME_TYPE)) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val written =
                    withContext(Dispatchers.IO) {
                        val jsonl = viewModel.exportJsonl() ?: return@withContext false
                        try {
                            context.contentResolver
                                .openOutputStream(
                                    uri,
                                )?.use { it.write(jsonl.encodeToByteArray()) } !=
                                null
                        } catch (_: IOException) {
                            false
                        }
                    }
                if (!written) Toast.makeText(context, R.string.export_failed, Toast.LENGTH_LONG).show()
            }
        }
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
                onOpenSettings = {
                    val appDetails = Uri.fromParts("package", context.packageName, null)
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appDetails))
                },
                onEnableBluetooth = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                onDismissError = viewModel::dismissError,
                onPractice = { device ->
                    // An input has one reader: practice opens the piano once the monitor has closed it.
                    scope.launch {
                        viewModel.setDeviceOpen(false)
                        onPractice(device)
                    }
                },
            ),
    )
}
