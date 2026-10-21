package app.silverbox.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Trips to the phone's camera: from the tap on "Shoot" until the camera's last picture has landed in the
 * gallery and gone onto the film. Runs in the app's own scope and keeps its place in the settings, so a
 * screen turned round or the app killed while the camera was open loses nothing.
 */
class Trips(
    private val context: Context,
    private val db: LabDatabase,
    private val vault: Vault,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val changed: suspend () -> Unit,
) {
    private val lock = Mutex()
    private var watching: Pair<Long, Job>? = null

    private val _loaded = MutableStateFlow<Pickup.Loaded?>(null)
    /** What the last trip put on the film so far; null when there is nothing to say. */
    val loaded: StateFlow<Pickup.Loaded?> = _loaded

    private val _looking = MutableStateFlow(false)
    /** The first few seconds back from the camera, while most pictures are still landing. */
    val looking: StateFlow<Boolean> = _looking

    private val _toBin = MutableStateFlow<List<Uri>>(emptyList())
    /** Pictures now on the film, waiting for the keeper's leave to go to the gallery's bin. */
    val toBin: StateFlow<List<Uri>> = _toBin

    /** The camera is about to open: what it takes from now on goes on the film. */
    suspend fun start(now: Long = System.currentTimeMillis()) {
        settings.update { it.copy(shootingSince = now, shootingBack = 0) }
        _loaded.value = null
    }

    /** The app is in front again: if the camera was out, the trip is over and its pictures are collected. */
    fun back() {
        scope.launch {
            val s = settings.now()
            if (s.shootingSince <= 0) return@launch
            if (s.shootingBack <= 0) {
                val now = System.currentTimeMillis()
                // A resume in the instant the camera is being opened (after the permission dialog) is not a return.
                if (now - s.shootingSince < 2000) return@launch
                settings.update { it.copy(shootingBack = now) }
            }
            watch()
        }
    }

    /** Called at start-up: a trip the app was killed in the middle of collecting carries on. */
    fun resume() {
        scope.launch { if (settings.now().shootingBack > 0) watch() }
    }

    fun dismiss() { _loaded.value = null }

    fun binned(uris: List<Uri>) = _toBin.update { it - uris.toSet() }

    private suspend fun watch() {
        val s = settings.now()
        val since = s.shootingSince
        val back = s.shootingBack
        if (since <= 0 || back <= 0) return
        synchronized(this) {
            if (watching?.let { it.first == since && it.second.isActive } == true) return
            watching = since to scope.launch { collect(since, back) }
        }
    }

    private suspend fun collect(since: Long, back: Long) {
        _looking.value = true
        // Say "nothing new" once, at the first look; after that only news, so a dismissed note stays gone.
        var said = false
        try {
            for (at in Pickup.LOOKS) {
                val wait = back + at - System.currentTimeMillis()
                if (wait > 0) delay(wait)
                if (at >= 4_000L) _looking.value = false
                if (!Pickup.canRead(context)) continue
                val got = lock.withLock { runCatching { Pickup.load(context, db, vault, since, back) }.getOrDefault(Pickup.Loaded()) }
                if (!got.empty || !said) _loaded.update { (it ?: Pickup.Loaded()) + got }
                said = true
                if (got.uris.isNotEmpty()) {
                    _toBin.update { it + got.uris }
                    changed()
                }
            }
        } finally {
            _looking.value = false
            // Only this trip's own marks: a new trip may have started meanwhile.
            settings.update { if (it.shootingSince == since) it.copy(shootingSince = 0, shootingBack = 0) else it }
        }
    }
}
