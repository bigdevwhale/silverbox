package app.silverbox.ui.lab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.silverbox.BuildConfig
import app.silverbox.R
import app.silverbox.container
import app.silverbox.domain.Rules
import app.silverbox.ui.Go
import app.silverbox.ui.LabData
import app.silverbox.ui.T
import app.silverbox.ui.art.stockLabel
import app.silverbox.ui.components.PrimaryButton
import app.silverbox.ui.components.SectionTitle
import app.silverbox.ui.components.SelectCard
import app.silverbox.ui.components.TopBar
import app.silverbox.ui.ctx
import app.silverbox.ui.theme.U
import app.silverbox.ui.theme.UType

/** The lab counter: a receipt for the roll, ordinary or express development, and handing it over. */
@Composable
fun SendScreen(id: Long, data: LabData, go: Go) {
    val c = ctx()
    val roll = data.roll(id)
    if (roll == null || !roll.inCamera()) {
        // Already handed over (or gone): back to the counter.
        androidx.compose.runtime.LaunchedEffect(Unit) { go.home() }
        return
    }
    val frames = data.framesOf(id)
    // Debug builds develop in a minute, so the whole loop can be tried in one sitting.
    val fast = BuildConfig.DEBUG && !BuildConfig.DEMO
    val expressOpen = Rules.expressOpen(data.rolls, data.now, unlimited = fast)
    var express by remember { mutableStateOf(false) }
    val left = roll.capacity - frames.size

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding().navigationBarsPadding()) {
        TopBar(stringResource(R.string.lab_title), onBack = { go.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Receipt {
                Line(stringResource(R.string.receipt_shop), center = true, big = true)
                Line(stringResource(R.string.receipt_sub), center = true)
                Dashes()
                Line(stringResource(R.string.receipt_date), T.stampDate(data.now) + " " + T.time(c, data.now))
                Line(stringResource(R.string.receipt_film), stockLabel(roll.stockOf))
                Line(
                    stringResource(if (roll.movie) R.string.receipt_clips else R.string.receipt_frames),
                    if (roll.movie) frames.size.toString() else "${frames.size} / ${roll.capacity}",
                )
                if (roll.name.isNotBlank()) Line(stringResource(R.string.receipt_name), roll.name)
                Line(stringResource(R.string.receipt_service), stringResource(if (express) R.string.receipt_express else R.string.receipt_standard))
                Dashes()
                Line(stringResource(R.string.receipt_ready), T.whenAt(c, data.now + if (fast) 60_000 else if (express) Rules.EXPRESS else Rules.STANDARD, data.today))
                Line(stringResource(R.string.receipt_thanks), center = true)
            }

            SectionTitle(stringResource(R.string.service))
            SelectCard(!express, { express = false }, U.c.teal) {
                Column {
                    Text(stringResource(R.string.service_standard), style = UType.Strong)
                    Text(stringResource(R.string.service_standard_hint), style = UType.Muted)
                }
            }
            Spacer(Modifier.height(8.dp))
            SelectCard(express, { if (expressOpen) express = true }, U.c.brick) {
                Column {
                    Text(stringResource(R.string.service_express), style = UType.Strong.copy(color = if (expressOpen) U.c.ink else U.c.faint))
                    Text(
                        if (expressOpen) stringResource(R.string.service_express_hint)
                        else stringResource(R.string.service_express_next, T.whenAt(c, Rules.expressFrom(data.rolls), data.today)),
                        style = UType.Muted,
                    )
                }
            }
            if (!roll.movie && left > 0) {
                Spacer(Modifier.height(14.dp))
                Text(pluralStringResource(R.plurals.send_blank, left, left), style = UType.Muted)
            }
            if (roll.expired > 0 || Rules.freshDaysLeft(roll.loadedAt, data.now) <= 0) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.send_expired), style = UType.Muted.copy(color = U.c.brick))
            }
            Spacer(Modifier.height(20.dp))
        }
        PrimaryButton(
            stringResource(R.string.send_do),
            {
                c.container.write { lab.send(roll, express, fast = fast) }
                go.home()
            },
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun Receipt(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Column(Modifier.fillMaxWidth().background(U.c.paper).padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            content()
        }
        // The torn bottom edge.
        val paper = U.c.paper
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val n = 30
            val step = size.width / n
            val p = Path().apply {
                moveTo(0f, 0f)
                for (i in 0 until n) {
                    lineTo(i * step + step / 2, size.height)
                    lineTo((i + 1) * step, 0f)
                }
                close()
            }
            drawPath(p, paper)
            drawLine(paper, Offset(0f, 0f), Offset(size.width, 0f), 2f)
        }
    }
}

@Composable
private fun Line(left: String, right: String = "", center: Boolean = false, big: Boolean = false) {
    val style = UType.Mono.copy(fontSize = if (big) 16.sp else 13.sp)
    if (center) Text(left, style = style.copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
    else Row(Modifier.fillMaxWidth()) {
        Text(left, style = style.copy(color = U.c.muted), modifier = Modifier.weight(1f))
        Text(right, style = style, textAlign = TextAlign.End)
    }
}

@Composable
private fun Dashes() {
    Text("- ".repeat(40), style = UType.Mono.copy(color = U.c.faint), maxLines = 1, softWrap = false)
}
