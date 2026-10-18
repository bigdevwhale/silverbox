package app.silverbox.ui.album

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.silverbox.R
import app.silverbox.container
import app.silverbox.data.Frame
import app.silverbox.ui.Go
import app.silverbox.ui.LabData
import app.silverbox.ui.Scope
import app.silverbox.ui.T
import app.silverbox.ui.art.Print
import app.silverbox.ui.components.Segmented
import app.silverbox.ui.ctx
import app.silverbox.ui.theme.Hand
import app.silverbox.ui.theme.U
import app.silverbox.ui.theme.UType

private enum class View { ALBUM, BOX }

/** The family album (chosen prints, two to a page) and the shoebox (every print, loose). */
@Composable
fun AlbumTab(data: LabData, go: Go) {
    var view by rememberSaveable { mutableStateOf(View.ALBUM) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(if (view == View.ALBUM) R.string.album_title else R.string.box_title), style = UType.Display.copy(fontSize = 28.sp))
            Text(
                if (view == View.ALBUM) pluralStringResource(R.plurals.prints, data.album.size, data.album.size)
                else pluralStringResource(R.plurals.prints, data.box.size, data.box.size),
                style = UType.Muted,
            )
            Spacer(Modifier.height(12.dp))
            Segmented(
                View.entries, view,
                { stringResource(if (it == View.ALBUM) R.string.album else R.string.box) },
                { view = it },
            )
        }
        when (view) {
            View.ALBUM -> if (data.album.isEmpty()) Empty(R.string.album_empty, R.string.album_empty_text) else Album(data.album, go)
            View.BOX -> if (data.box.isEmpty()) Empty(R.string.box_empty, R.string.box_empty_text) else Shoebox(data.box, go)
        }
    }
}

@Composable
private fun Empty(title: Int, text: Int) {
    Column(Modifier.fillMaxSize().padding(36.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(title), style = UType.Title.copy(textAlign = TextAlign.Center))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(text), style = UType.Sub.copy(textAlign = TextAlign.Center))
    }
}

/** Pages of the album: cream card, prints held by little black corners, a line written beneath each. */
@Composable
private fun Album(list: List<Frame>, go: Go) {
    val pages = list.chunked(2)
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        itemsIndexed(pages) { i, page ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(U.RSm)
                    .background(U.c.albumPage)
                    .border(1.dp, U.c.line, U.RSm)
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                page.forEachIndexed { j, f -> Mounted(f, if ((i + j) % 2 == 0) Alignment.Start else Alignment.End) { go.print(f.id, Scope.ALBUM) } }
                Text("— ${i + 1} —", style = UType.Small.copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Mounted(f: Frame, align: Alignment.Horizontal, onClick: () -> Unit) {
    val c = ctx()
    val bmp = c.container.vault.small(f.print)
    val landscape = bmp?.let { it.width >= it.height } ?: true
    Column(Modifier.fillMaxWidth(), horizontalAlignment = align) {
        Box(Modifier.fillMaxWidth(if (landscape) 0.92f else 0.62f).clickable(onClick = onClick)) {
            Print(bmp, Modifier.fillMaxWidth(), landscape = landscape, border = 6.dp)
            Corners(Modifier.matchParentSize())
        }
        Spacer(Modifier.height(6.dp))
        Text(
            f.note.ifBlank { T.date(c, f.takenAt) }, fontFamily = Hand, fontSize = 21.sp, lineHeight = 23.sp, color = U.c.albumInk,
            maxLines = 2, modifier = Modifier.fillMaxWidth(if (landscape) 0.92f else 0.62f),
        )
    }
}

/** The four black paper corners that hold a print in an album. */
@Composable
private fun Corners(modifier: Modifier) {
    val ink = U.c.corners
    Canvas(modifier) {
        val s = size.minDimension * 0.11f
        val o = -s * 0.22f
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawPath(Path().apply { moveTo(x, y); lineTo(x + dx * s, y); lineTo(x, y + dy * s); close() }, ink)
        }
        corner(o, o, 1f, 1f)
        corner(size.width - o, o, -1f, 1f)
        corner(o, size.height - o, 1f, -1f)
        corner(size.width - o, size.height - o, -1f, -1f)
        drawCircle(Color.White.copy(alpha = 0.08f), 1f, Offset.Zero)
    }
}

/** Every print, loose in a box, a little askew; newest on top. */
@Composable
private fun Shoebox(list: List<Frame>, go: Go) {
    val c = ctx()
    val vault = c.container.vault
    LazyVerticalGrid(
        GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        var month = ""
        val withHeaders = buildList<Any> {
            list.forEach { f ->
                val m = T.local(f.takenAt).let { "%04d-%02d".format(it.year, it.monthValue) }
                if (m != month) { add(f.takenAt to m); month = m }
                add(f)
            }
        }
        items(withHeaders, span = { if (it is Frame) GridItemSpan(1) else GridItemSpan(maxLineSpan) }) { item ->
            if (item is Frame) {
                val tilt = ((item.id * 53) % 9 - 4).toFloat() * 0.8f
                val bmp = vault.small(item.print)
                Box(Modifier.fillMaxWidth().padding(4.dp), contentAlignment = Alignment.Center) {
                    Print(
                        bmp, Modifier.fillMaxWidth(if (bmp?.let { it.width >= it.height } != false) 1f else 0.72f).rotate(tilt).clickable { go.print(item.id, Scope.BOX) },
                        border = 4.dp,
                    )
                }
            } else {
                val (ms, _) = item as Pair<Long, String>
                Text(
                    T.local(ms).format(java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy", c.resources.configuration.locales[0])).replaceFirstChar { it.titlecase() },
                    style = UType.Eyebrow, modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                )
            }
        }
    }
}
