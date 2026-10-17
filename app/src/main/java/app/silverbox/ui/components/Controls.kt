package app.silverbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.silverbox.ui.theme.U
import app.silverbox.ui.theme.UType

/** − value + */
@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier, color: Color = U.c.teal) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = UType.Body, modifier = Modifier.weight(1f))
        RoundIcon(Icons.Rounded.Remove, color, onMinus)
        Text(value, style = UType.Number.copy(textAlign = TextAlign.Center), modifier = Modifier.width(96.dp))
        RoundIcon(Icons.Rounded.Add, color, onPlus)
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(20.dp), tint = color) }
}

@Composable
fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit, color: Color = U.c.teal) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = UType.Body)
            if (sub != null) Text(sub, style = UType.Muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = color, checkedThumbColor = U.c.surface, uncheckedTrackColor = U.c.surface2, uncheckedBorderColor = U.c.line),
        )
    }
}

/** A choice among a few, drawn as a card with a tick. */
@Composable
fun SelectCard(
    selected: Boolean,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(U.RMd)
            .background(if (selected) color.copy(alpha = 0.12f) else U.c.surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) color.copy(alpha = 0.7f) else U.c.line, U.RMd)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.weight(1f)) { content() }
        trailing()
        if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = color, modifier = Modifier.size(22.dp))
    }
}

/** A small number with a caption, for stat rows. */
@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier, color: Color = U.c.ink) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = UType.Number.copy(color = color), maxLines = 1)
        Text(label, style = UType.Small.copy(textAlign = TextAlign.Center), maxLines = 2)
    }
}
