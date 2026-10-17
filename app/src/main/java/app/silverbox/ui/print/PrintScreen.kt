package app.silverbox.ui.print

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.silverbox.R
import app.silverbox.container
import app.silverbox.data.Frame
import app.silverbox.data.Lab
import app.silverbox.ui.Go
import app.silverbox.ui.LabData
import app.silverbox.ui.Scope
import app.silverbox.ui.T
import app.silverbox.ui.art.Print
import app.silverbox.ui.art.rememberFull
import app.silverbox.ui.art.stockLabel
import app.silverbox.ui.components.TopBar
import app.silverbox.ui.ctx
import app.silverbox.ui.envelope.Heart
import app.silverbox.ui.theme.Hand
import app.silverbox.ui.theme.Mono
import app.silverbox.ui.theme.U
import app.silverbox.ui.theme.UType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One print at a time, paged through its roll, the album or the shoebox. Tap to turn it over and write on the back. */
@Composable
fun PrintScreen(id: Long, scope: Scope, data: LabData, go: Go) {
    val c = ctx()
    val list = remember(scope, data.frames, data.rolls) {
        when (scope) {
            Scope.ROLL -> data.frame(id)?.let { f -> data.framesOf(f.rollId).filter { it.print.isNotEmpty() } }.orEmpty()
            Scope.ALBUM -> data.album
            Scope.BOX -> data.box
        }
    }
    if (list.isEmpty()) return
    val start = list.indexOfFirst { it.id == id }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { list.size }
    var back by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(pager.currentPage) { back = false }
    val current = list.getOrNull(pager.currentPage) ?: list[0]
    val roll = data.roll(current.rollId)
    val coroutines = androidx.compose.runtime.rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding().navigationBarsPadding()) {
        TopBar(roll?.let { T.rollName(c, it) } ?: "", onBack = { go.back() }) {
            IconButton(onClick = { coroutines.launch { share(c, current) } }) { Icon(Icons.Rounded.Share, stringResource(R.string.share), tint = U.c.ink) }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { page ->
            val f = list[page]
            Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
                Turnable(f, back && page == pager.currentPage, data) { back = !back }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text(
                listOfNotNull(T.date(c, current.takenAt), roll?.let { stockLabel(it.stockOf) }, "${current.number} / ${roll?.capacity ?: list.size}").joinToString(" · "),
                style = UType.Mono.copy(fontSize = 12.sp, color = U.c.muted),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Heart(current.album) { on -> c.container.write { db.dao().frame(current.id)?.let { db.dao().update(it.copy(album = on)) } } }
                Row(
                    Modifier.clickable { back = !back }.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.RotateRight, null, tint = U.c.teal)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (back) R.string.print_front else R.string.print_back), style = UType.Button.copy(color = U.c.teal))
                }
            }
        }
    }
}

/** The print, turning over in 3D to its back. */
@Composable
private fun Turnable(f: Frame, back: Boolean, data: LabData, onTap: () -> Unit) {
    val angle by animateFloatAsState(if (back) 180f else 0f, tween(520), label = "turn")
    val bmp = rememberFull(f.print)
    val landscape = bmp?.let { it.width >= it.height } ?: true
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val m = Modifier
            .then(if (landscape) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(0.78f))
            .graphicsLayer {
                rotationY = angle
                cameraDistance = 14 * density
            }
        if (angle <= 90f) Print(bmp, m.clickable(onClick = onTap), landscape = landscape, border = 10.dp)
        else Back(f, data, landscape, m.graphicsLayer { rotationY = 180f }, onTap)
    }
}

/** The back of a print: the lab's stamp, and what the family wrote on it. */
@Composable
private fun Back(f: Frame, data: LabData, landscape: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val c = ctx()
    val roll = data.roll(f.rollId)
    var note by remember(f.id) { mutableStateOf(f.note) }
    var by by remember(f.id) { mutableStateOf(f.by) }
    LaunchedEffect(note, by) {
        if (note == f.note && by == f.by) return@LaunchedEffect
        delay(500)
        c.container.write { db.dao().frame(f.id)?.let { db.dao().update(it.copy(note = note.trim(), by = by.trim())) } }
    }
    Column(
        modifier
            .aspectRatio(if (landscape) 1.5f else 1 / 1.5f)
            .shadow(5.dp, RoundedCornerShape(2.dp))
            .background(U.c.paper)
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onTap)) {
            Text(
                "SILVERBOX LAB  №" + (roll?.let { T.order(it.order) } ?: "—") + "  " + T.stampDate(f.takenAt) + "  " + (roll?.let { stockLabel(it.stockOf).uppercase() } ?: ""),
                fontFamily = Mono, fontSize = 9.sp, color = U.c.faint, maxLines = 1,
            )
        }
        Spacer(Modifier.height(10.dp))
        BasicTextField(
            note, { note = it.take(240) },
            Modifier.fillMaxWidth().weight(1f),
            textStyle = UType.Hand.copy(fontSize = 25.sp, lineHeight = 28.sp),
            cursorBrush = SolidColor(U.c.teal),
            decorationBox = { inner ->
                Box {
                    if (note.isEmpty()) Text(stringResource(R.string.print_write), fontFamily = Hand, fontSize = 25.sp, color = U.c.faint)
                    inner()
                }
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.print_by), fontFamily = Hand, fontSize = 20.sp, color = U.c.muted)
            Spacer(Modifier.width(6.dp))
            BasicTextField(
                by, { by = it.take(30) }, Modifier.weight(1f),
                singleLine = true,
                textStyle = UType.Hand.copy(fontSize = 22.sp),
                cursorBrush = SolidColor(U.c.teal),
                decorationBox = { inner ->
                    Box {
                        if (by.isEmpty()) Text(data.settings.keeper.ifBlank { "…" }, fontFamily = Hand, fontSize = 22.sp, color = U.c.faint, textAlign = TextAlign.Start)
                        inner()
                    }
                },
            )
        }
    }
}

/** Shares the print with its white border, the way it came out of the envelope. */
private suspend fun share(c: Context, f: Frame) {
    val file = withContext(Dispatchers.IO) {
        val vault = c.container.vault
        val bmp = vault.full(f.print, 2400) ?: return@withContext null
        val framed = Lab.bordered(bmp)
        bmp.recycle()
        val dir = File(c.cacheDir, "share").apply { mkdirs() }
        val out = File(dir, "Silverbox_" + T.stampDate(f.takenAt).replace('.', '-') + "_" + f.number + ".jpg")
        out.outputStream().use { framed.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        framed.recycle()
        out
    } ?: return
    val uri = FileProvider.getUriForFile(c, c.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    c.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
