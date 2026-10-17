package app.silverbox.ui.shelf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.silverbox.R
import app.silverbox.data.Roll
import app.silverbox.ui.Go
import app.silverbox.ui.LabData
import app.silverbox.ui.T
import app.silverbox.ui.art.Cassette
import app.silverbox.ui.art.FilmBox
import app.silverbox.ui.ctx
import app.silverbox.ui.theme.Hand
import app.silverbox.ui.theme.Mono
import app.silverbox.ui.theme.U
import app.silverbox.ui.theme.UType

/** Every developed roll on a wooden shelf, boxes and cassettes with hand-written labels. */
@Composable
fun ShelfTab(data: LabData, go: Go) {
    val c = ctx()
    val films = data.shelf.count { it.movie }
    val prints = data.box.size
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.shelf_title), style = UType.Display.copy(fontSize = 28.sp))
            Text(
                listOf(
                    T.plural(c, R.plurals.rolls, data.shelf.size - films),
                    T.plural(c, R.plurals.prints, prints),
                    T.plural(c, R.plurals.films, films),
                ).joinToString(" · "),
                style = UType.Muted,
            )
        }
        if (data.shelf.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(36.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.shelf_empty), style = UType.Title.copy(textAlign = TextAlign.Center))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.shelf_empty_text), style = UType.Sub.copy(textAlign = TextAlign.Center))
            }
            return
        }
        // Rows of three on shelf boards.
        val rows = data.shelf.chunked(3)
        LazyVerticalGrid(GridCells.Fixed(1), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(rows, span = { GridItemSpan(1) }) { row -> Board(row, data, go) }
        }
    }
}

@Composable
private fun Board(row: List<Roll>, data: LabData, go: Go) {
    val c = ctx()
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            row.forEach { r ->
                Column(Modifier.weight(1f).clickable { go.roll(r.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth(0.86f)) {
                        if (r.movie) Cassette(r.stockOf, Modifier.fillMaxWidth()) else FilmBox(r.stockOf, r.capacity, Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(T.rollName(c, r), fontFamily = Hand, fontSize = 18.sp, lineHeight = 19.sp, color = U.c.ink, maxLines = 2, textAlign = TextAlign.Center)
                    Text("№" + T.order(r.order) + " · " + T.plural(c, if (r.movie) R.plurals.clips else R.plurals.frames, data.framesOf(r.id).size), fontFamily = Mono, fontSize = 10.sp, color = U.c.faint, maxLines = 1)
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        // The board itself.
        Canvas(Modifier.fillMaxWidth().height(16.dp).padding(top = 4.dp)) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFB98B5A), Color(0xFF8E6338))), size = Size(size.width, size.height * 0.7f))
            drawRect(Color.Black.copy(alpha = 0.12f), Offset(0f, size.height * 0.7f), Size(size.width, size.height * 0.3f))
        }
    }
}
