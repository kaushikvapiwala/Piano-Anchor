package pianoanchor.app

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/** Starts dependency injection (Koin) for the whole app; the wiring is in [appModule]. */
class PianoAnchorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@PianoAnchorApplication)
            modules(appModule)
        }
    }
}
