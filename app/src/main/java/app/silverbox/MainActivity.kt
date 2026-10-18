package app.silverbox

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import app.silverbox.notify.Alarms
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.silverbox.data.Appearance
import app.silverbox.data.Settings
import app.silverbox.ui.SilverboxRoot
import app.silverbox.ui.theme.SilverboxTheme

class MainActivity : AppCompatActivity() {
    /** Counts taps on "Shoot" in a reminder; each new one opens the camera. */
    private var shootRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent?.action == Alarms.SHOOT) shootRequests++
        enableEdgeToEdge()
        setContent {
            val settings: Settings? by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val system = isSystemInDarkTheme()
            val dark = when (settings?.appearance) {
                Appearance.LIGHT -> false
                Appearance.DARK -> true
                else -> system
            }
            // Cream paper wants dark icons in the bars; the darkroom wants light ones.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            SilverboxTheme(dark) {
                // Wait for settings so the welcome screen doesn't flash for someone who has film loaded.
                settings?.let { SilverboxRoot(it, shootRequests) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Alarms.SHOOT) shootRequests++
    }
}
