package app.silverbox.ui.lab

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.silverbox.R
import app.silverbox.container
import app.silverbox.data.Roll
import app.silverbox.domain.Format
import app.silverbox.domain.Rules
import app.silverbox.domain.Stock
import app.silverbox.ui.Go
import app.silverbox.ui.LabData
import app.silverbox.ui.T
import app.silverbox.ui.art.FilmBox
import app.silverbox.ui.art.stockLabel
import app.silverbox.ui.components.Chip
import app.silverbox.ui.components.PrimaryButton
import app.silverbox.ui.components.SectionTitle
import app.silverbox.ui.components.TopBar
import app.silverbox.ui.components.UTextField
import app.silverbox.ui.ctx
import app.silverbox.ui.theme.U
import app.silverbox.ui.theme.UType

/** The film shelf of the shop: pick a stock, how many frames, and what the roll is for. */
@Composable
fun LoadScreen(format: Format, data: LabData, go: Go) {
    val c = ctx()
    val stocks = if (format == Format.MOVIE) Stock.movie else Stock.photo
    var stock by remember { mutableStateOf(stocks.first()) }
    var size by remember { mutableStateOf(Rules.SIZES.first()) }
    var name by remember { mutableStateOf("") }
    val already = if (format == Format.MOVIE) data.cassette else data.camera

    // Something is in the camera already (loaded on another screen): nothing to do here.
    LaunchedEffect(Unit) { if (already != null) go.back() }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding().navigationBarsPadding()) {
        TopBar(stringResource(if (format == Format.MOVIE) R.string.load_cassette_title else R.string.load_title), onBack = { go.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                stringResource(if (format == Format.MOVIE) R.string.load_cassette_hint else R.string.load_hint), style = UType.Sub,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(18.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(stocks) { s ->
                    val on = s == stock
                    val k by animateFloatAsState(if (on) 1f else 0.9f, label = "pick")
                    Box(Modifier.width(118.dp).scale(k).clickable { stock = s }.padding(vertical = 8.dp)) {
                        FilmBox(s, if (format == Format.PHOTO) size else 0, Modifier.fillMaxWidth())
                    }
                }
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(14.dp))
                Column(Modifier.fillMaxWidth().clip(U.RMd).background(U.c.paper).border(1.dp, U.c.line, U.RMd).padding(16.dp)) {
                    Text(stockLabel(stock), style = UType.Title)
                    Spacer(Modifier.height(4.dp))
                    Text(T.stockDesc(c, stock), style = UType.Sub)
                }
                if (format == Format.PHOTO) {
                    SectionTitle(stringResource(R.string.exposures))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Rules.SIZES.forEach { n -> Chip(n.toString(), size == n, { size = n }) }
                    }
                }
                SectionTitle(stringResource(R.string.roll_for))
                UTextField(name, { name = it.take(40) }, placeholder = stringResource(if (format == Format.MOVIE) R.string.cassette_for_hint else R.string.roll_for_hint))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(if (format == Format.MOVIE) R.string.cassette_rules else R.string.roll_rules), style = UType.Muted)
                Spacer(Modifier.height(24.dp))
            }
        }
        PrimaryButton(
            stringResource(R.string.load_do),
            {
                val cont = c.container
                cont.write {
                    if (db.dao().loaded(format.name) == null) {
                        db.dao().insert(
                            Roll(
                                format = format.name, stock = stock.name, capacity = if (format == Format.MOVIE) Rules.CASSETTE else size,
                                name = name.trim(), loadedAt = System.currentTimeMillis(),
                            )
                        )
                    }
                }
                go.back()
            },
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}
