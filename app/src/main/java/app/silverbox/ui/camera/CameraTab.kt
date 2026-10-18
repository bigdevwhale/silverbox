package app.silverbox.ui.camera

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.silverbox.R
import app.silverbox.container
import app.silverbox.data.Frame
import app.silverbox.data.Pickup
import app.silverbox.data.Roll
import app.silverbox.domain.Format
import app.silverbox.domain.Rules
import app.silverbox.ui.Go
import app.silverbox.ui.LabData
import app.silverbox.ui.Scope
import app.silverbox.ui.T
import app.silverbox.ui.art.Canister
import app.silverbox.ui.art.Cassette
import app.silverbox.ui.art.Counter
import app.silverbox.ui.art.Envelope
import app.silverbox.ui.art.Print
import app.silverbox.ui.art.stockLabel
import app.silverbox.ui.components.PrimaryButton
import app.silverbox.ui.components.SectionTitle
import app.silverbox.ui.components.SoftButton
import app.silverbox.ui.components.UCard
import app.silverbox.ui.ctx
import app.silverbox.ui.theme.Hand
import app.silverbox.ui.theme.Lora
import app.silverbox.ui.theme.Mono
import app.silverbox.ui.theme.U
import app.silverbox.ui.theme.UType

/** The counter of the shop and the camera on it: envelopes to collect, the roll being shot, orders at the lab. */
@Composable
fun CameraTab(data: LabData, go: Go, loaded: Pickup.Loaded?, dismissLoaded: () -> Unit) {
    val c = ctx()
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Silverbox", style = UType.Display.copy(fontSize = 28.sp))
                Text(stringResource(R.string.tagline), style = UType.Muted)
            }
            IconButton(onClick = { go.settings() }) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings_title), tint = U.c.muted) }
        }
        Spacer(Modifier.height(12.dp))

        if (!Pickup.canRead(c) && data.camera != null && !app.silverbox.BuildConfig.DEMO) NoAccessCard(go)

        data.waiting.forEach { r ->
            ReadyCard(r, data, go)
            Spacer(Modifier.height(12.dp))
        }

        loaded?.let { LoadedCard(it, dismissLoaded) }

        if (data.shooting) ShootingCard(go)

        val roll = data.camera
        if (roll != null) RollCard(roll, data.framesOf(roll.id), data, go) else EmptyCamera(go)

        Spacer(Modifier.height(12.dp))
        val cassette = data.cassette
        if (cassette != null) CassetteCard(cassette, data.framesOf(cassette.id), go) else NoCassette(go)

        if (data.atLab.isNotEmpty()) {
            SectionTitle(stringResource(R.string.at_lab))
            UCard {
                data.atLab.forEachIndexed { i, r ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    LabRow(r, data)
                }
            }
        }

        if (roll != null) ShotList(roll)

        data.yearsAgo.firstOrNull()?.let { f -> YearsAgo(f, data, go) }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun NoAccessCard(go: Go) {
    UCard(Modifier.padding(bottom = 12.dp)) {
        Text(stringResource(R.string.no_access_title), style = UType.Strong)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.no_access_text), style = UType.Muted)
        Spacer(Modifier.height(10.dp))
        SoftButton(stringResource(R.string.no_access_button), { go.shoot() })
    }
}

/** An envelope waiting on the counter. */
@Composable
private fun ReadyCard(r: Roll, data: LabData, go: Go) {
    val c = ctx()
    val bob = rememberInfiniteTransition(label = "bob").animateFloat(-1.5f, 1.5f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "tilt")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(U.RLg)
            .background(U.c.teal)
            .clickable { go.envelope(r.id) }
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ready_eyebrow).uppercase(), style = UType.Eyebrow.copy(color = U.c.mustardSoft))
                Spacer(Modifier.height(6.dp))
                Text(stringResource(if (r.movie) R.string.ready_film else R.string.ready_prints), style = UType.Title.copy(color = U.c.paper))
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.order_line, T.order(r.order), stockLabel(r.stockOf)) + " · " +
                        T.plural(c, if (r.movie) R.plurals.clips else R.plurals.frames, data.framesOf(r.id).size),
                    style = UType.Mono.copy(color = U.c.tealSoft, fontSize = 12.sp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Envelope(0f, Modifier.width(96.dp).rotate(bob.value - 6f))
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            stringResource(if (r.movie) R.string.ready_open_film else R.string.ready_open), { go.envelope(r.id) }, Modifier.fillMaxWidth(),
            color = U.c.mustard, content = U.c.onMustard,
        )
    }
}

@Composable
private fun LoadedCard(l: Pickup.Loaded, dismiss: () -> Unit) {
    val c = ctx()
    val lines = buildList {
        if (l.photos > 0) add(T.plural(c, R.plurals.loaded_photos, l.photos))
        if (l.clips > 0) add(T.plural(c, R.plurals.loaded_clips, l.clips))
        if (l.leftover > 0) add(T.plural(c, R.plurals.loaded_leftover, l.leftover))
        if (l.noRoll > 0) add(T.plural(c, R.plurals.loaded_no_roll, l.noRoll))
    }
    if (lines.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(U.RMd).background(U.c.mustardSoft).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) { lines.forEach { Text(it, style = UType.Body.copy(fontSize = 14.sp)) } }
        IconButton(onClick = dismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.close), tint = U.c.muted) }
    }
}

@Composable
private fun ShootingCard(go: Go) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(U.RMd).background(U.c.brickSoft).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val blink = rememberInfiniteTransition(label = "rec").animateFloat(0.25f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
            Box(Modifier.size(10.dp).clip(CircleShape).background(U.c.brick.copy(alpha = blink.value)))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.shooting_title), style = UType.Strong)
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.shooting_text), style = UType.Muted)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftButton(stringResource(R.string.shooting_back), { go.shoot() }, Modifier.weight(1f), color = U.c.brick, soft = U.c.surface, icon = Icons.Rounded.CameraAlt)
            SoftButton(stringResource(R.string.shooting_done), { go.stopShooting() }, color = U.c.muted, soft = U.c.surface)
        }
    }
}

/** The roll in the camera: its cartridge, the frame counter and what to do next. */
@Composable
private fun RollCard(roll: Roll, frames: List<Frame>, data: LabData, go: Go) {
    val c = ctx()
    val n = frames.size
    val full = n >= roll.capacity
    UCard(padding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        Text(stringResource(R.string.in_camera).uppercase(), style = UType.Eyebrow)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canister(roll.stockOf, Modifier.width(120.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(T.rollName(c, roll), style = UType.Hand.copy(fontSize = 26.sp, color = U.c.ink), maxLines = 2)
                Text(stockLabel(roll.stockOf) + " · " + stringResource(R.string.loaded_on, T.day(c, roll.loadedAt)), style = UType.Muted)
                Spacer(Modifier.height(8.dp))
                Counter(n, roll.capacity)
            }
        }
        val left = Rules.freshDaysLeft(roll.loadedAt, data.now)
        when {
            full -> Note(stringResource(R.string.roll_full), U.c.teal)
            left <= 0 -> Note(stringResource(R.string.roll_expired), U.c.brick)
            left <= 14 -> Note(pluralStringResource(R.plurals.roll_fresh_left, left, left), U.c.muted)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!full) PrimaryButton(stringResource(R.string.shoot), { go.shoot() }, Modifier.weight(1f), icon = Icons.Rounded.CameraAlt)
            if (n > 0) {
                if (full) PrimaryButton(stringResource(R.string.to_lab), { go.send(roll.id) }, Modifier.weight(1f), icon = Icons.Rounded.LocalShipping)
                else SoftButton(stringResource(R.string.to_lab_short), { go.send(roll.id) }, icon = Icons.Rounded.LocalShipping)
            }
        }
    }
}

@Composable
private fun Note(text: String, color: Color) {
    Spacer(Modifier.height(12.dp))
    Text(text, style = UType.Muted.copy(color = color))
}

@Composable
private fun EmptyCamera(go: Go) {
    UCard(padding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        Text(stringResource(R.string.in_camera).uppercase(), style = UType.Eyebrow)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.camera_empty), style = UType.Title)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.camera_empty_text), style = UType.Sub)
        Spacer(Modifier.height(14.dp))
        PrimaryButton(stringResource(R.string.load_film), { go.load(Format.PHOTO) }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun CassetteCard(roll: Roll, frames: List<Frame>, go: Go) {
    val c = ctx()
    UCard(padding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cassette(roll.stockOf, Modifier.width(72.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.cassette).uppercase(), style = UType.Eyebrow)
                Spacer(Modifier.height(2.dp))
                Text(T.rollName(c, roll), style = UType.Hand.copy(fontSize = 22.sp, color = U.c.ink), maxLines = 1)
                Text(stringResource(R.string.cassette_feet, frames.size, roll.capacity) + " · " + stockLabel(roll.stockOf), style = UType.Mono.copy(fontSize = 12.sp, color = U.c.muted))
            }
        }
        if (frames.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SoftButton(stringResource(R.string.to_lab_short), { go.send(roll.id) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.LocalShipping)
        }
    }
}

@Composable
private fun NoCassette(go: Go) {
    Row(
        Modifier.fillMaxWidth().clip(U.RMd).border(1.dp, U.c.line, U.RMd).clickable { go.load(Format.MOVIE) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cassette(app.silverbox.domain.Stock.HOME8, Modifier.width(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.no_cassette), style = UType.Body.copy(fontSize = 14.sp))
            Text(stringResource(R.string.no_cassette_hint), style = UType.Small)
        }
    }
}

@Composable
private fun LabRow(r: Roll, data: LabData) {
    val c = ctx()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("№" + T.order(r.order), style = UType.Mono.copy(fontSize = 15.sp, color = U.c.teal), modifier = Modifier.width(66.dp))
        Column(Modifier.weight(1f)) {
            Text(T.rollName(c, r) + " · " + stockLabel(r.stockOf), style = UType.Body.copy(fontSize = 14.sp), maxLines = 1)
            val ready = r.readyAt ?: 0
            Text(
                if (data.now >= ready) stringResource(R.string.lab_developing)
                else stringResource(if (r.express) R.string.lab_ready_express else R.string.lab_ready, T.whenAt(c, ready, data.today)),
                style = UType.Small,
            )
        }
    }
}

/** A few things worth a frame on this roll, chosen by the roll. */
@Composable
private fun ShotList(roll: Roll) {
    val all = stringArrayResource(R.array.prompts)
    val picks = remember(roll.id, all.size) {
        val r = java.util.Random(roll.id * 31 + roll.loadedAt / 86_400_000L)
        all.indices.shuffled(r).take(3).map { all[it] }
    }
    SectionTitle(stringResource(R.string.shot_list))
    Column(
        Modifier.fillMaxWidth().clip(U.RMd).background(U.c.paper).border(1.dp, U.c.line, U.RMd).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        picks.forEach { p ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("□", fontFamily = Mono, fontSize = 16.sp, color = U.c.faint)
                Spacer(Modifier.width(10.dp))
                Text(p, fontFamily = Hand, fontSize = 22.sp, color = U.c.ink)
            }
        }
    }
}

@Composable
private fun YearsAgo(f: Frame, data: LabData, go: Go) {
    val c = ctx()
    val years = data.today.year - T.local(f.takenAt).year
    SectionTitle(stringResource(R.string.on_this_day))
    Row(
        Modifier.fillMaxWidth().clip(U.RMd).background(U.c.surface).border(1.dp, U.c.line, U.RMd).clickable { go.print(f.id, Scope.BOX) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Print(c.container.vault.small(f.print), Modifier.width(110.dp).rotate(-3f), border = 4.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(pluralStringResource(R.plurals.years_ago, years, years), style = UType.Name.copy(fontFamily = Lora))
            if (f.note.isNotBlank()) Text(f.note, fontFamily = Hand, fontSize = 20.sp, color = U.c.muted, maxLines = 2)
        }
    }
}
