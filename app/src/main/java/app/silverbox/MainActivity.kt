package app.silverbox

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.silverbox.data.Settings
import app.silverbox.ui.SilverboxRoot
import app.silverbox.ui.theme.SilverboxTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Cream paper: dark icons in the bars.
        val style = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        setContent {
            val settings: Settings? by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            SilverboxTheme {
                // Wait for settings so the welcome screen doesn't flash for someone who has film loaded.
                settings?.let { SilverboxRoot(it) }
            }
        }
    }
}
