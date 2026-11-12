package pianoanchor.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.jetbrains.compose.resources.stringResource
import pianoanchor.ui.shell.PianoAnchorApp
import pianoanchor.ui.shell.resources.Res
import pianoanchor.ui.shell.resources.app_name

fun main() =
    application {
        Window(onCloseRequest = ::exitApplication, title = stringResource(Res.string.app_name)) {
            PianoAnchorApp()
        }
    }
