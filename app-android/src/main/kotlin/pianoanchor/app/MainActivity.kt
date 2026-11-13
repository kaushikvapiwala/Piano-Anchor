package pianoanchor.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import pianoanchor.feature.monitor.MonitorActions
import pianoanchor.feature.monitor.MonitorScreen
import pianoanchor.feature.monitor.MonitorViewModel
import pianoanchor.ui.shell.PianoAnchorTheme

private const val FIXTURE_MIME_TYPE = "application/x-ndjson"
private const val FIXTURE_FILE_NAME = "piano-anchor-fixture.jsonl"
private val BLUETOOTH_PERMISSIONS = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PianoAnchorTheme { MonitorRoute() } }
    }
}

/** Platform glue for the note monitor: Bluetooth permission, settings intents and the SAF export. */
@Composable
private fun MonitorRoute(viewModel: MonitorViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Scan whatever the answer: without permission the adapter reports it and the screen offers the settings.
    val askBluetooth =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { viewModel.scanBluetooth() }
    val export =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FIXTURE_MIME_TYPE)) { uri ->
            val jsonl = viewModel.exportJsonl()
            if (uri != null && jsonl != null) {
                scope.launch(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(jsonl.encodeToByteArray()) }
                }
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
            ),
    )
}
