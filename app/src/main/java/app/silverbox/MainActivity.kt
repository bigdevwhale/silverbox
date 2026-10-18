package app.silverbox

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import app.silverbox.notify.Alarms
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.silverbox.data.Settings
import app.silverbox.ui.SilverboxRoot
import app.silverbox.ui.theme.SilverboxTheme

class MainActivity : AppCompatActivity() {
    /** Counts taps on "Shoot" in a reminder; each new one opens the camera. */
    private var shootRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent?.action == Alarms.SHOOT) shootRequests++
        // Cream paper: dark icons in the bars.
        val style = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        setContent {
            val settings: Settings? by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            SilverboxTheme {
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
