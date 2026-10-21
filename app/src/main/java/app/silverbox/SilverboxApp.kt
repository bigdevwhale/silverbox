package app.silverbox

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import app.silverbox.data.DemoSeed
import app.silverbox.data.Gallery
import app.silverbox.data.Lab
import app.silverbox.data.LabDatabase
import app.silverbox.data.SettingsStore
import app.silverbox.data.Trips
import app.silverbox.data.Vault
import app.silverbox.notify.Alarms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppContainer(private val context: Context) {
    val db = LabDatabase.create(context)
    val settings = SettingsStore(context)
    val vault = Vault(context)
    val lab = Lab(context, db, vault, settings)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val trips = Trips(context, db, vault, settings, scope) { changed() }

    /** Fire-and-forget so a write survives the screen being closed right after tapping. */
    fun write(block: suspend AppContainer.() -> Unit) {
        scope.launch {
            block()
            changed()
        }
    }

    /** With "give back" on, the originals of opened envelopes go to the phone's gallery; ones already there are skipped. */
    suspend fun giveBack() {
        if (!settings.now().giveBack || Gallery.needsPermission(context)) return
        withContext(Dispatchers.IO) {
            runCatching { Gallery.restore(context, db.dao().rolls().filter { it.opened }, db.dao().frames(), vault) }
                .onFailure { android.util.Log.w("Gallery", "Giving back failed", it) }
        }
    }

    /** Re-arm the envelope notice and let the lab get on with anything waiting. */
    suspend fun changed() {
        Alarms.reschedule(context)
        scope.launch {
            // A roll whose time came while the lab was still busy with it: say so now.
            if (lab.work() > 0) {
                Alarms.fire(context)
                Alarms.reschedule(context)
            }
        }
    }
}

class SilverboxApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Alarms.ensureChannels(this)
        container.trips.resume()
        // Re-arm on every start: OEM task killers (HyperOS & co.) silently drop alarms.
        container.scope.launch {
            if (BuildConfig.DEMO) DemoSeed.seedIfEmpty(this@SilverboxApp, container)
            container.changed()
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as SilverboxApp).container

/** Resources in the in-app language, for receivers that AppCompat doesn't localise before Android 13. */
fun Context.localized(): Context {
    val tags = AppCompatDelegate.getApplicationLocales()
    if (tags.isEmpty) return this
    val config = Configuration(resources.configuration)
    config.setLocale(tags[0])
    return createConfigurationContext(config)
}
