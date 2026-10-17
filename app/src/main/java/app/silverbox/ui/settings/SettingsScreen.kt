package app.silverbox.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.silverbox.BuildConfig
import app.silverbox.R
import app.silverbox.container
import app.silverbox.data.Backup
import app.silverbox.data.Settings
import app.silverbox.ui.Go
import app.silverbox.ui.LabData
import app.silverbox.ui.Language
import app.silverbox.ui.components.Chip
import app.silverbox.ui.components.ConfirmDialog
import app.silverbox.ui.components.InfoRow
import app.silverbox.ui.components.SectionTitle
import app.silverbox.ui.components.SwitchRow
import app.silverbox.ui.components.TopBar
import app.silverbox.ui.components.UCard
import app.silverbox.ui.components.UTextField
import app.silverbox.ui.theme.U
import app.silverbox.ui.theme.UType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(data: LabData, go: Go) {
    val context = LocalContext.current
    val c = context.container
    val s = data.settings
    var keeper by remember { mutableStateOf(s.keeper) }
    var status by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    fun set(change: (Settings) -> Settings) = c.write { settings.update(change) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        c.scope.launch {
            val ok = runCatching { withContext(Dispatchers.IO) { Backup.write(context, uri, c.db.dao().rolls(), c.db.dao().frames(), c.vault) } }.isSuccess
            status = context.getString(if (ok) R.string.backup_done else R.string.backup_failed)
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding().navigationBarsPadding()) {
        TopBar(stringResource(R.string.settings_title), onBack = { go.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle(stringResource(R.string.set_shooting))
            UCard {
                SwitchRow(
                    stringResource(R.string.set_hide),
                    stringResource(if (Build.VERSION.SDK_INT >= 30) R.string.set_hide_hint else R.string.set_hide_old),
                    s.hide, { on -> set { it.copy(hide = on) } },
                )
                SwitchRow(stringResource(R.string.set_stamp), stringResource(R.string.set_stamp_hint), s.dateStamp, { on -> set { it.copy(dateStamp = on) } })
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.set_keeper), style = UType.Body)
                Text(stringResource(R.string.set_keeper_hint), style = UType.Muted)
                Spacer(Modifier.height(8.dp))
                UTextField(keeper, { keeper = it.take(30); set { st -> st.copy(keeper = keeper.trim()) } }, placeholder = stringResource(R.string.set_keeper_placeholder))
            }

            SectionTitle(stringResource(R.string.set_notify_section))
            UCard {
                SwitchRow(stringResource(R.string.set_notify), stringResource(R.string.set_notify_hint), s.notify, { on -> set { it.copy(notify = on) } })
            }

            SectionTitle(stringResource(R.string.set_language))
            UCard {
                val cur = Language.current()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.lang_system), cur == Language.SYSTEM, { Language.set(Language.SYSTEM) })
                    Chip("English", cur == Language.EN, { Language.set(Language.EN) })
                    Chip("Русский", cur == Language.RU, { Language.set(Language.RU) })
                }
            }

            SectionTitle(stringResource(R.string.set_backup))
            UCard {
                Text(stringResource(R.string.set_backup_hint), style = UType.Muted)
                InfoRow(Icons.Rounded.Upload, stringResource(R.string.backup_export), stringResource(R.string.backup_export_hint), onClick = { export.launch("Silverbox.zip") })
                InfoRow(Icons.Rounded.Download, stringResource(R.string.backup_import), stringResource(R.string.backup_import_hint), onClick = { import.launch(arrayOf("application/zip", "application/octet-stream")) })
                status?.let { Text(it, style = UType.Muted.copy(color = U.c.teal)) }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.about, BuildConfig.VERSION_NAME), style = UType.Small.copy(textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(40.dp))
        }
    }

    restoreUri?.let { uri ->
        ConfirmDialog(
            stringResource(R.string.backup_import), stringResource(R.string.backup_import_confirm), stringResource(R.string.backup_import_do),
            onConfirm = {
                c.scope.launch {
                    val ok = runCatching {
                        withContext(Dispatchers.IO) {
                            val (rolls, frames, dir) = Backup.read(context, uri)
                            c.vault.wipe()
                            c.vault.adopt(dir)
                            c.db.dao().replaceAll(rolls, frames)
                        }
                    }.isSuccess
                    c.settings.update { it.copy(onboarded = true) }
                    c.changed()
                    status = context.getString(if (ok) R.string.backup_restored else R.string.backup_bad)
                }
            },
            onDismiss = { restoreUri = null }, danger = true,
        )
    }
}
