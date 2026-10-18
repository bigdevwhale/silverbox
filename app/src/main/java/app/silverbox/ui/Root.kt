package app.silverbox.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.silverbox.BuildConfig
import app.silverbox.R
import app.silverbox.container
import app.silverbox.data.Frame
import app.silverbox.data.Pickup
import app.silverbox.data.Roll
import app.silverbox.data.Settings
import app.silverbox.domain.Format
import app.silverbox.notify.Alarms
import app.silverbox.ui.album.AlbumTab
import app.silverbox.ui.camera.CameraTab
import app.silverbox.ui.cinema.CinemaScreen
import app.silverbox.ui.components.rememberNow
import app.silverbox.ui.envelope.EnvelopeScreen
import app.silverbox.ui.lab.LoadScreen
import app.silverbox.ui.lab.SendScreen
import app.silverbox.ui.onboarding.OnboardingScreen
import app.silverbox.ui.print.PrintScreen
import app.silverbox.ui.settings.SettingsScreen
import app.silverbox.ui.shelf.RollScreen
import app.silverbox.ui.shelf.ShelfTab
import app.silverbox.ui.theme.Nunito
import app.silverbox.ui.theme.U
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

enum class Tab { CAMERA, ALBUM, SHELF }

/** Which prints a print screen pages through. */
enum class Scope { ROLL, ALBUM, BOX }

/** Everything stored, plus what is derived from it, recomputed every half minute or on change. */
@Immutable
class LabData(val rolls: List<Roll>, val frames: List<Frame>, val settings: Settings, val now: Long) {
    val zone: ZoneId = ZoneId.systemDefault()
    val today: LocalDate = LocalDate.now(zone)
    private val byRoll = frames.groupBy { it.rollId }
    fun framesOf(id: Long): List<Frame> = byRoll[id].orEmpty().sortedBy { it.number }
    fun roll(id: Long) = rolls.firstOrNull { it.id == id }
    fun frame(id: Long) = frames.firstOrNull { it.id == id }

    val camera: Roll? = rolls.firstOrNull { it.inCamera() && it.formatOf == Format.PHOTO }
    val cassette: Roll? = rolls.firstOrNull { it.inCamera() && it.formatOf == Format.MOVIE }
    val waiting: List<Roll> = rolls.filter { it.waiting(now) }.sortedBy { it.readyAt }
    val atLab: List<Roll> = rolls.filter { it.atLab(now) }.sortedBy { it.readyAt }
    /** Opened rolls, newest first: the shelf. */
    val shelf: List<Roll> = rolls.filter { it.opened }.sortedByDescending { it.sentAt }
    private val openIds = shelf.map { it.id }.toSet()
    /** Every print out of an opened envelope, newest first: the shoebox. */
    val box: List<Frame> = frames.filter { it.rollId in openIds && it.print.isNotEmpty() && !it.video }.sortedByDescending { it.takenAt }
    /** The prints chosen for the album, oldest first, the way an album fills. */
    val album: List<Frame> = box.filter { it.album }.sortedBy { it.takenAt }
    val yearsAgo: List<Frame> = app.silverbox.domain.Rules.yearsAgo(box, today, zone)
    val shooting: Boolean get() = settings.shootingSince > 0 && now - settings.shootingSince < Pickup.SESSION
}

/** Everything a screen can open. */
class Go(private val nav: NavHostController, val shoot: () -> Unit, val stopShooting: () -> Unit) {
    fun load(format: Format) = nav.navigate("load/${format.name}") { launchSingleTop = true }
    fun send(id: Long) = nav.navigate("send/$id") { launchSingleTop = true }
    fun envelope(id: Long) = nav.navigate("envelope/$id") { launchSingleTop = true }
    fun print(id: Long, scope: Scope) = nav.navigate("print/$id/${scope.name}")
    fun roll(id: Long) = nav.navigate("roll/$id")
    fun cinema(id: Long) = nav.navigate("cinema/$id")
    fun settings() = nav.navigate("settings")
    fun home() = nav.popBackStack("main", false)
    fun back() = nav.popBackStack()
    fun replaceWithRoll(id: Long) = nav.navigate("roll/$id") { popUpTo("main") }
}

@Composable
fun SilverboxRoot(settings: Settings, shootRequests: Int = 0) {
    val context = LocalContext.current
    val container = context.container
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.CAMERA) }
    val start = remember { if (settings.onboarded) "main" else "welcome" }

    val rolls by container.db.dao().observeRolls().collectAsStateWithLifecycle(initialValue = null)
    val frames by container.db.dao().observeFrames().collectAsStateWithLifecycle(initialValue = null)
    val tick = rememberNow(30_000)
    val data = remember(rolls, frames, settings, tick) { LabData(rolls.orEmpty(), frames.orEmpty(), settings, System.currentTimeMillis()) }
    var loaded by remember { mutableStateOf<Pickup.Loaded?>(null) }

    val trash = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}
    val askRead = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (Pickup.canRead(context)) startShooting(context) else if (granted.isNotEmpty()) openAppSettings(context)
    }
    // Onboarding only asks; the camera opens when there is film to shoot.
    var readTick by remember { mutableStateOf(0) }
    val askReadOnly = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        readTick++
        if (!Pickup.canRead(context) && granted.isNotEmpty() && granted.values.none { it }) openAppSettings(context)
    }
    val go = remember(nav) {
        Go(
            nav,
            shoot = {
                if (!Pickup.canRead(context)) askRead.launch(Pickup.permissions())
                else startShooting(context)
            },
            stopShooting = { container.write { this.settings.update { it.copy(shootingSince = 0) } } },
        )
    }

    // Back from the camera: whatever it took goes onto the film. Pending clips may land a moment later.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            val s = container.settings.now()
            if (s.shootingSince <= 0 || !Pickup.canRead(context)) return@launch
            val got = mutableListOf<Pickup.Loaded>()
            for (wait in listOf(0L, 1500L)) {
                delay(wait)
                got += Pickup.load(context, container.db, container.vault, s.shootingSince)
            }
            val uris = got.flatMap { it.uris }
            val sum = Pickup.Loaded(uris, got.sumOf { it.photos }, got.sumOf { it.clips }, got.sumOf { it.leftover }, got.sumOf { it.noRoll })
            if (sum.photos + sum.clips + sum.leftover + sum.noRoll > 0) loaded = sum
            if (System.currentTimeMillis() - s.shootingSince > Pickup.SESSION) container.settings.update { it.copy(shootingSince = 0) }
            if (s.hide && uris.isNotEmpty()) {
                Pickup.trashRequest(context, uris)?.let { trash.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
            }
            container.changed()
        }
    }

    // "Shoot" on a reminder: straight to the camera, once per tap.
    var shotHandled by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(shootRequests) {
        if (shootRequests > shotHandled && settings.onboarded) {
            shotHandled = shootRequests
            Alarms.clearRemind(context)
            go.shoot()
        }
    }

    // Onboarded elsewhere (a restored backup, the demo seed): leave the welcome screens.
    LaunchedEffect(settings.onboarded) {
        if (settings.onboarded && nav.currentDestination?.route == "welcome") nav.navigate("main") { popUpTo("welcome") { inclusive = true } }
    }
    LaunchedEffect(data.waiting.size) { if (data.waiting.isNotEmpty()) Alarms.clear(context) }
    // The note about what went on the film belongs to that roll: gone once it is at the lab.
    LaunchedEffect(data.camera?.id) { if (data.camera == null) loaded = null }

    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        container.scope.launch { container.changed() }
    }
    LaunchedEffect(data.atLab.isNotEmpty()) {
        // Asked when it first matters: the first roll at the lab.
        if (data.atLab.isNotEmpty() && settings.notify && Build.VERSION.SDK_INT >= 33 && !Alarms.canNotify(context) && !BuildConfig.DEMO) {
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    NavHost(
        navController = nav,
        startDestination = start,
        modifier = Modifier.background(U.c.bg),
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(160)) },
    ) {
        composable("welcome") {
            OnboardingScreen(readTick, askRead = { askReadOnly.launch(Pickup.permissions()) }) {
                container.write { this.settings.update { it.copy(onboarded = true) } }
                nav.navigate("main") { popUpTo("welcome") { inclusive = true } }
                go.load(Format.PHOTO)
            }
        }
        composable("main") { MainTabs(tab, { tab = it }, go, data, loaded) { loaded = null } }
        composable("load/{format}", arguments = listOf(navArgument("format") { type = NavType.StringType })) { e ->
            LoadScreen(runCatching { Format.valueOf(e.arguments?.getString("format").orEmpty()) }.getOrDefault(Format.PHOTO), data, go)
        }
        composable("send/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            SendScreen(e.arguments?.getLong("id") ?: 0L, data, go)
        }
        composable("envelope/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            EnvelopeScreen(e.arguments?.getLong("id") ?: 0L, data, go)
        }
        composable(
            "print/{id}/{scope}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }, navArgument("scope") { type = NavType.StringType }),
        ) { e ->
            PrintScreen(e.arguments?.getLong("id") ?: 0L, runCatching { Scope.valueOf(e.arguments?.getString("scope").orEmpty()) }.getOrDefault(Scope.ROLL), data, go)
        }
        composable("roll/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            RollScreen(e.arguments?.getLong("id") ?: 0L, data, go)
        }
        composable("cinema/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
            CinemaScreen(e.arguments?.getLong("id") ?: 0L, data, go)
        }
        composable("settings") { SettingsScreen(data, go) }
    }
}

/** Hands the shutter to the phone's own camera; what it takes from now on goes on the film. */
private fun startShooting(context: android.content.Context) {
    if (!Pickup.hasCamera(context)) {
        Toast.makeText(context, R.string.no_camera, Toast.LENGTH_LONG).show()
        return
    }
    val c = context.container
    c.scope.launch {
        c.settings.update { s ->
            // Keep the session that is already running, so going back and forth loses nothing.
            val now = System.currentTimeMillis()
            s.copy(shootingSince = if (s.shootingSince > 0 && now - s.shootingSince < Pickup.SESSION) s.shootingSince else now)
        }
        runCatching { context.startActivity(Pickup.cameraIntent()) }
    }
}

private fun openAppSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun MainTabs(tab: Tab, onTab: (Tab) -> Unit, go: Go, data: LabData, loaded: Pickup.Loaded?, dismissLoaded: () -> Unit) {
    Column(Modifier.fillMaxSize().background(U.c.bg)) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.CAMERA -> CameraTab(data, go, loaded, dismissLoaded)
                Tab.ALBUM -> AlbumTab(data, go)
                Tab.SHELF -> ShelfTab(data, go)
            }
        }
        HorizontalDivider(color = U.c.line)
        Row(
            Modifier.fillMaxWidth().background(U.c.surface).navigationBarsPadding().height(64.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(Icons.Rounded.CameraAlt, stringResource(R.string.tab_camera), tab == Tab.CAMERA, badge = data.waiting.isNotEmpty()) { onTab(Tab.CAMERA) }
            NavItem(Icons.Rounded.Collections, stringResource(R.string.tab_album), tab == Tab.ALBUM) { onTab(Tab.ALBUM) }
            NavItem(Icons.Rounded.Inventory2, stringResource(R.string.tab_shelf), tab == Tab.SHELF) { onTab(Tab.SHELF) }
        }
    }
}

@Composable
private fun RowScope.NavItem(icon: ImageVector, label: String, selected: Boolean, badge: Boolean = false, onClick: () -> Unit) {
    val color = if (selected) U.c.teal else U.c.faint
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(if (selected) U.c.tealSoft else Color.Transparent).padding(horizontal = 18.dp, vertical = 4.dp),
            ) { Icon(icon, null, Modifier.size(22.dp), tint = color) }
            if (badge) Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 10.dp).size(9.dp).clip(RoundedCornerShape(50)).background(U.c.stamp))
        }
        Spacer(Modifier.height(3.dp))
        Text(label, fontFamily = Nunito, fontSize = 11.5.sp, color = color, maxLines = 1)
    }
}
