package app.silverbox.ui.shelf

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.silverbox.R
import app.silverbox.container
import app.silverbox.data.Frame
import app.silverbox.ui.Go
import app.silverbox.ui.LabData
import app.silverbox.ui.Scope
import app.silverbox.ui.T
import app.silverbox.ui.art.Cassette
import app.silverbox.ui.art.FilmBox
import app.silverbox.ui.art.FilmStrip
import app.silverbox.ui.art.NEGATIVE
import app.silverbox.ui.art.NEGATIVE_BW
import app.silverbox.ui.art.stockLabel
import app.silverbox.ui.components.ConfirmDialog
import app.silverbox.ui.components.PrimaryButton
import app.silverbox.ui.components.Segmented
import app.silverbox.ui.components.SoftButton
import app.silverbox.ui.components.TopBar
import app.silverbox.ui.components.UTextField
import app.silverbox.ui.ctx
import app.silverbox.ui.theme.U
import app.silverbox.ui.theme.UType

/** A roll from the shelf: its contact sheet or its negatives, strip by strip; a cassette's film. */
@Composable
fun RollScreen(id: Long, data: LabData, go: Go) {
    val c = ctx()
    val roll = data.roll(id) ?: return
    val frames = data.framesOf(id)
    var negative by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var name by remember(roll.name) { mutableStateOf(roll.name) }
    var deleting by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding().navigationBarsPadding()) {
        TopBar(stockLabel(roll.stockOf) + " · №" + T.order(roll.order), onBack = { go.back() }) {
            IconButton(onClick = { deleting = true }) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.roll_delete), tint = U.c.muted) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(84.dp)) {
                    if (roll.movie) Cassette(roll.stockOf, Modifier.fillMaxWidth()) else FilmBox(roll.stockOf, roll.capacity, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    if (editing) {
                        UTextField(name, { name = it.take(40) }, placeholder = stringResource(R.string.roll_for_hint))
                        Spacer(Modifier.height(6.dp))
                        SoftButton(stringResource(R.string.save), {
                            editing = false
                            c.container.write { db.dao().roll(id)?.let { db.dao().update(it.copy(name = name.trim())) } }
                        })
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { editing = true }) {
                            Text(T.rollName(c, roll), style = UType.Hand.copy(fontSize = 28.sp, color = U.c.ink), modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Rounded.Edit, stringResource(R.string.rename), tint = U.c.faint, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    Text(T.span(c, frames), style = UType.Muted)
                    Text(
                        T.plural(c, if (roll.movie) R.plurals.clips else R.plurals.frames, frames.size) +
                            (if (roll.expired > 0.05f) " · " + c.getString(R.string.roll_was_expired) else ""),
                        style = UType.Small,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            if (roll.movie && roll.film.isNotEmpty()) {
                Row(Modifier.padding(horizontal = 18.dp)) {
                    PrimaryButton(stringResource(R.string.film_watch), { go.cinema(id) }, Modifier.weight(1f), icon = Icons.Rounded.PlayArrow)
                    Spacer(Modifier.width(8.dp))
                    SoftButton(stringResource(R.string.share), {
                        val f = c.container.vault.file(roll.film)
                        val uri = FileProvider.getUriForFile(c, c.packageName + ".files", f)
                        val send = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        c.startActivity(Intent.createChooser(send, null))
                    }, icon = Icons.Rounded.Share)
                }
                Spacer(Modifier.height(16.dp))
            }
            if (!roll.movie) {
                Segmented(
                    listOf(false, true), negative,
                    { stringResource(if (it) R.string.negatives else R.string.contact_sheet) },
                    { negative = it },
                    Modifier.padding(horizontal = 18.dp),
                )
                Spacer(Modifier.height(14.dp))
            }
            Sheet(frames, roll.capacity, negative, roll.stockOf.bw, go)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (deleting) ConfirmDialog(
        stringResource(R.string.roll_delete), stringResource(R.string.roll_delete_text), stringResource(R.string.roll_delete_do),
        onConfirm = {
            c.container.write {
                db.dao().framesOf(id).forEach { vault.delete(it.original); vault.delete(it.print) }
                db.dao().roll(id)?.let { vault.delete(it.film); db.dao().deleteFramesOf(id); db.dao().delete(it) }
            }
            go.back()
        },
        onDismiss = { deleting = false }, danger = true,
    )
}

/** The roll cut into strips of four, the way the lab sleeves negatives; blanks where nothing was shot. */
@Composable
private fun Sheet(frames: List<Frame>, capacity: Int, negative: Boolean, bw: Boolean, go: Go) {
    val vault = ctx().container.vault
    val count = maxOf(frames.size, if (negative) capacity else frames.size)
    val byNumber = frames.associateBy { it.number }
    for (start in 1..count step 4) {
        FilmStrip(minOf(4, count - start + 1).let { 4 }, first = start) { i ->
            val f = byNumber[start + i]
            val bmp = f?.let { vault.small(it.print) }
            if (start + i > count) return@FilmStrip
            androidx.compose.foundation.layout.BoxWithConstraints(
                Modifier.fillMaxSize().clipToBounds().background(if (negative) androidx.compose.ui.graphics.Color(if (bw) 0xFFD8D6D0 else 0xFFD9925A) else androidx.compose.ui.graphics.Color(0xFF2A2420)),
                contentAlignment = Alignment.Center,
            ) {
                if (bmp != null) {
                    // On film every frame is landscape: an upright picture lies on its side.
                    val upright = bmp.height > bmp.width
                    androidx.compose.foundation.Image(
                        bmp.asImageBitmap(), null,
                        (if (upright) Modifier.requiredSize(maxHeight, maxWidth).rotate(-90f) else Modifier.fillMaxSize())
                            .clickable { go.print(f.id, Scope.ROLL) },
                        contentScale = ContentScale.Crop,
                        colorFilter = if (negative) androidx.compose.ui.graphics.ColorFilter.colorMatrix(if (bw) NEGATIVE_BW else NEGATIVE) else null,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}
