package app.silverbox.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class Settings(
    val onboarded: Boolean = false,
    /** Move what the camera took out of the gallery once it is on the film, so nobody peeks. */
    val hide: Boolean = true,
    /** Print the date in the corner, the way old point-and-shoots did. */
    val dateStamp: Boolean = true,
    /** A notice when an envelope is ready. */
    val notify: Boolean = true,
    /** The camera was opened from the app at this moment; what it takes since then goes on the film. 0 when not shooting. */
    val shootingSince: Long = 0,
    /** The keeper's name, written on the backs of prints as the photographer. */
    val keeper: String = "",
    /** The projector's clatter while a film plays. */
    val projector: Boolean = true,
    /** Nudges to take a picture, this many a day; 0 for none. */
    val reminders: Int = 2,
    /** The window they come in, minutes after midnight. */
    val remindFrom: Int = 10 * 60,
    val remindTo: Int = 20 * 60,
    /** A developed print can be turned to the picture the camera took, before the lab. */
    val originals: Boolean = false,
    /** How the app looks: as the system, the cream shop, or the darkroom. */
    val appearance: Appearance = Appearance.SYSTEM,
)

enum class Appearance { SYSTEM, LIGHT, DARK }

class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<Settings> = store.data.map { it.read() }

    private fun Preferences.read() = Settings(
        onboarded = this[ONBOARDED] ?: false,
        hide = this[HIDE] ?: true,
        dateStamp = this[DATE_STAMP] ?: true,
        notify = this[NOTIFY] ?: true,
        shootingSince = this[SHOOTING] ?: 0L,
        keeper = this[KEEPER] ?: "",
        projector = this[PROJECTOR] ?: true,
        reminders = this[REMINDERS] ?: 2,
        remindFrom = this[REMIND_FROM] ?: 10 * 60,
        remindTo = this[REMIND_TO] ?: 20 * 60,
        originals = this[ORIGINALS] ?: false,
        appearance = this[APPEARANCE]?.let { runCatching { Appearance.valueOf(it) }.getOrNull() } ?: Appearance.SYSTEM,
    )

    suspend fun now(): Settings = settings.first()

    suspend fun update(change: (Settings) -> Settings) {
        store.edit {
            val s = change(it.read())
            it[ONBOARDED] = s.onboarded
            it[HIDE] = s.hide
            it[DATE_STAMP] = s.dateStamp
            it[NOTIFY] = s.notify
            it[SHOOTING] = s.shootingSince
            it[KEEPER] = s.keeper
            it[PROJECTOR] = s.projector
            it[REMINDERS] = s.reminders
            it[REMIND_FROM] = s.remindFrom
            it[REMIND_TO] = s.remindTo
            it[ORIGINALS] = s.originals
            it[APPEARANCE] = s.appearance.name
        }
    }

    private companion object {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val HIDE = booleanPreferencesKey("hide")
        val DATE_STAMP = booleanPreferencesKey("date_stamp")
        val NOTIFY = booleanPreferencesKey("notify")
        val SHOOTING = longPreferencesKey("shooting_since")
        val KEEPER = stringPreferencesKey("keeper")
        val PROJECTOR = booleanPreferencesKey("projector")
        val REMINDERS = intPreferencesKey("reminders")
        val REMIND_FROM = intPreferencesKey("remind_from")
        val REMIND_TO = intPreferencesKey("remind_to")
        val ORIGINALS = booleanPreferencesKey("originals")
        val APPEARANCE = stringPreferencesKey("appearance")
    }
}
