package app.silverbox.ui.envelope

import app.silverbox.ui.theme.Paper
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.silverbox.R
import app.silverbox.container
import app.silverbox.data.Frame
import app.silverbox.data.Roll
import app.silverbox.ui.Go
import app.silverbox.ui.LabData
import app.silverbox.ui.Scope
import app.silverbox.ui.T
import app.silverbox.ui.art.Cassette
import app.silverbox.ui.art.Envelope
import app.silverbox.ui.art.Print
import app.silverbox.ui.art.rememberFull
import app.silverbox.ui.art.stockLabel
import app.silverbox.ui.components.PrimaryButton
import app.silverbox.ui.components.SoftButton
import app.silverbox.ui.components.TopBar
import app.silverbox.ui.ctx
import app.silverbox.ui.theme.Mono
import app.silverbox.ui.theme.U
import app.silverbox.ui.theme.UType
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Collecting an envelope: it lies shut on the counter, the flap lifts, and the prints come out as a
 * stack to go through one by one. The best go to the album with a heart.
 */
@Composable
fun EnvelopeScreen(id: Long, data: LabData, go: Go) {
    val c = ctx()
    val roll = data.roll(id) ?: return
    val frames = remember(id, data.frames) { data.framesOf(id).filter { it.print.isNotEmpty() } }
    var open by rememberSaveable { mutableStateOf(roll.opened) }
    val flap by animateFloatAsState(if (open) 1f else 0f, tween(700), label = "flap")
    var index by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(open) {
        if (open && !roll.opened) c.container.write {
            db.dao().markOpened(id)
            giveBack()
        }
    }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding().navigationBarsPadding()) {
        TopBar(stringResource(R.string.order_title, T.order(roll.order)), onBack = { go.back() })
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                !open || flap < 1f -> Closed(roll, data, flap) { open = true }
                roll.movie -> FilmCan(roll, frames, go)
                index < frames.size -> Stack(frames, index, { index = it }, go)
                else -> TheEnd(roll, frames, go) { index = 0 }
            }
        }
    }
}

@Composable
private fun Closed(roll: Roll, data: LabData, flap: Float, onOpen: () -> Unit) {
    val c = ctx()
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Envelope(flap, Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
            // The lab's label on the front pocket.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Column(
                    Modifier.padding(bottom = 22.dp).fillMaxWidth(0.62f).background(Paper.white.copy(alpha = 0.94f)).padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(stringResource(if (roll.movie) R.string.env_film else R.string.env_photos), fontFamily = Mono, fontSize = 15.sp, color = Paper.stamp)
                    Text("№ " + T.order(roll.order) + " · " + stockLabel(roll.stockOf), fontFamily = Mono, fontSize = 11.sp, color = Paper.ink)
                    Text(T.rollName(c, roll), style = UType.Hand.copy(fontSize = 20.sp, color = Paper.pen), maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(26.dp))
        Text(stringResource(R.string.env_tap), style = UType.Sub.copy(textAlign = TextAlign.Center))
        Spacer(Modifier.height(4.dp))
        Text(
            T.plural(c, if (roll.movie) R.plurals.clips else R.plurals.frames, data.framesOf(roll.id).size) + " · " + T.span(c, data.framesOf(roll.id)),
            style = UType.Small.copy(textAlign = TextAlign.Center),
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton(stringResource(R.string.env_open), onOpen, Modifier.fillMaxWidth(), color = U.c.mustard, content = U.c.onMustard)
    }
}

/** The pile of prints: the top one is dragged off to show the next; the next few peek out beneath. */
@Composable
private fun Stack(frames: List<Frame>, index: Int, onIndex: (Int) -> Unit, go: Go) {
    val c = ctx()
    val vault = c.container.vault
    val scope = rememberCoroutineScope()
    val dx = remember(index) { Animatable(0f) }
    val dy = remember(index) { Animatable(0f) }
    val top = frames[index]
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 26.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            val wide = maxWidth
            // The ones beneath, slightly askew.
            for (k in minOf(frames.size - 1, index + 3) downTo index + 1) {
                val f = frames[k]
                val tilt = ((f.id * 37) % 9 - 4).toFloat()
                Print(vault.small(f.print), Modifier.width(wide).graphicsLayer { rotationZ = tilt; translationY = (k - index) * 6f })
            }
            val tilt = ((top.id * 37) % 5 - 2).toFloat()
            Print(
                rememberFull(top.print, 1400), Modifier
                    .width(wide)
                    .offset { IntOffset(dx.value.roundToInt(), dy.value.roundToInt()) }
                    .graphicsLayer { rotationZ = tilt + dx.value / 40f }
                    .pointerInput(index) {
                        detectDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (abs(dx.value) > size.width * 0.28f) {
                                        // Off it goes, under the pile.
                                        launch { dy.animateTo(dy.value * 1.5f, tween(220)) }
                                        dx.animateTo(if (dx.value > 0) size.width * 1.4f else -size.width * 1.4f, tween(220))
                                        onIndex(index + 1)
                                    } else {
                                        launch { dy.animateTo(0f) }
                                        dx.animateTo(0f)
                                    }
                                }
                            },
                        ) { change, drag ->
                            change.consume()
                            scope.launch { dx.snapTo(dx.value + drag.x); dy.snapTo(dy.value + drag.y) }
                        }
                    }
                    .clickable { go.print(top.id, Scope.ROLL) },
            )
        }
        val written = listOf(T.date(c, top.takenAt), top.note).filter { it.isNotBlank() }.joinToString(" · ")
        Text(written, style = UType.Hand.copy(fontSize = 22.sp, color = U.c.muted), maxLines = 1)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1} / ${frames.size}", fontFamily = Mono, fontSize = 15.sp, color = U.c.muted, modifier = Modifier.weight(1f))
            Heart(top.album) { on -> c.container.write { db.dao().update(top.copy(album = on)) } }
            Spacer(Modifier.width(10.dp))
            SoftButton(stringResource(R.string.env_next), { onIndex(index + 1) })
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.env_swipe), style = UType.Small)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun Heart(on: Boolean, onChange: (Boolean) -> Unit) {
    val k by animateFloatAsState(if (on) 1.12f else 1f, label = "heart")
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (on) U.c.brickSoft else U.c.surface)
            .clickable { onChange(!on) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (on) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null,
            Modifier.size(22.dp).graphicsLayer { scaleX = k; scaleY = k }, tint = if (on) U.c.brick else U.c.muted,
        )
        Spacer(Modifier.width(6.dp))
        Text(stringResource(if (on) R.string.in_album else R.string.to_album), style = UType.Button.copy(color = if (on) U.c.brick else U.c.muted))
    }
}

@Composable
private fun TheEnd(roll: Roll, frames: List<Frame>, go: Go, again: () -> Unit) {
    val c = ctx()
    val picked = frames.count { it.album }
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.env_end), style = UType.Display.copy(textAlign = TextAlign.Center))
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.env_end_text, T.plural(c, R.plurals.prints, frames.size), T.plural(c, R.plurals.prints, picked)),
            style = UType.Sub.copy(textAlign = TextAlign.Center),
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton(stringResource(R.string.env_contact), { go.replaceWithRoll(roll.id) }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton(stringResource(R.string.env_again), again)
            SoftButton(stringResource(R.string.done), { go.home() })
        }
    }
}

/** A cassette comes back as a film on a reel. */
@Composable
private fun FilmCan(roll: Roll, frames: List<Frame>, go: Go) {
    val c = ctx()
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Cassette(roll.stockOf, Modifier.width(150.dp))
        Spacer(Modifier.height(20.dp))
        Text(T.rollName(c, roll), style = UType.Hand.copy(fontSize = 30.sp, color = U.c.ink, textAlign = TextAlign.Center))
        Text(T.plural(c, R.plurals.clips, frames.size) + " · " + T.span(c, frames), style = UType.Muted)
        Spacer(Modifier.height(24.dp))
        PrimaryButton(stringResource(R.string.film_watch), { go.cinema(roll.id) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
        Spacer(Modifier.height(10.dp))
        SoftButton(stringResource(R.string.env_contact), { go.replaceWithRoll(roll.id) })
    }
}
