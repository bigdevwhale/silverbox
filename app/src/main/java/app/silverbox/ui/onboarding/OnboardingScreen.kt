package app.silverbox.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.silverbox.R
import app.silverbox.data.Pickup
import app.silverbox.domain.Stock
import app.silverbox.ui.art.Canister
import app.silverbox.ui.art.Envelope
import app.silverbox.ui.art.FilmBox
import app.silverbox.ui.components.PrimaryButton
import app.silverbox.ui.components.SoftButton
import app.silverbox.ui.ctx
import app.silverbox.ui.theme.Mono
import app.silverbox.ui.theme.U
import app.silverbox.ui.theme.UType

/** Three pages: the idea, how a roll goes round, and the one permission it needs. */
@Composable
fun OnboardingScreen(readTick: Int, askRead: () -> Unit, onDone: () -> Unit) {
    val c = ctx()
    var page by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(28.dp))
            when (page) {
                0 -> {
                    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        FilmBox(Stock.SILVER, 24, Modifier.width(112.dp).offset(x = (-78).dp, y = 10.dp).rotate(-11f))
                        FilmBox(Stock.EVERY, 36, Modifier.width(112.dp).offset(x = 78.dp, y = 10.dp).rotate(10f))
                        FilmBox(Stock.SUNNY, 24, Modifier.width(124.dp))
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(stringResource(R.string.onb1_title), style = UType.Display.copy(textAlign = TextAlign.Center))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.onb1_text), style = UType.Sub.copy(textAlign = TextAlign.Center))
                }
                1 -> {
                    Canister(Stock.SUNNY, Modifier.width(170.dp))
                    Spacer(Modifier.height(22.dp))
                    Text(stringResource(R.string.onb2_title), style = UType.Display.copy(textAlign = TextAlign.Center))
                    Spacer(Modifier.height(18.dp))
                    listOf(R.string.onb2_s1, R.string.onb2_s2, R.string.onb2_s3, R.string.onb2_s4).forEachIndexed { i, s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(32.dp).clip(CircleShape).background(U.c.mustardSoft), contentAlignment = Alignment.Center) {
                                Text((i + 1).toString(), fontFamily = Mono, fontSize = 15.sp, color = U.c.ink)
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(stringResource(s), style = UType.Body, modifier = Modifier.weight(1f))
                        }
                    }
                }
                else -> {
                    Envelope(0.2f, Modifier.width(220.dp).rotate(-4f))
                    Spacer(Modifier.height(28.dp))
                    Text(stringResource(R.string.onb3_title), style = UType.Display.copy(textAlign = TextAlign.Center))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.onb3_text), style = UType.Sub.copy(textAlign = TextAlign.Center))
                    Spacer(Modifier.height(20.dp))
                    // readTick changes after the permission dialog, so this is looked at again.
                    if (!remember(readTick) { Pickup.canRead(c) }) SoftButton(stringResource(R.string.onb3_allow), askRead)
                    else Text(stringResource(R.string.onb3_allowed), style = UType.Muted.copy(color = U.c.teal))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.Center) {
            repeat(3) { i ->
                Box(Modifier.padding(4.dp).size(if (i == page) 10.dp else 7.dp).clip(CircleShape).background(if (i == page) U.c.teal else U.c.line))
            }
        }
        PrimaryButton(
            stringResource(if (page < 2) R.string.next else R.string.onb_start),
            { if (page < 2) page++ else onDone() },
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
    }
}
