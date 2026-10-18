package app.silverbox

import android.graphics.Bitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.test.core.app.ApplicationProvider
import app.silverbox.data.DemoSeed
import app.silverbox.data.Settings
import app.silverbox.ui.SilverboxRoot
import app.silverbox.ui.theme.SilverboxTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the main screens with the demo family to PNGs (run with `-Prender`): a look without a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi")
class RenderScreens {
    @get:Rule
    val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val out = File(System.getProperty("render.out") ?: "build/art", "screens").apply { mkdirs() }
    private val photos = File(System.getProperty("render.photos") ?: "src/demo/assets/demo")

    private fun settle(ms: Long = 3000) {
        rule.mainClock.autoAdvance = false
        var t = 0L
        while (t < ms) {
            Thread.sleep(60)
            rule.mainClock.advanceTimeBy(100)
            t += 100
        }
    }

    private fun shot(name: String) {
        // Draw the window by hand: capturing waits for a frame, which a paused clock never gives.
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        rule.mainClock.advanceTimeByFrame()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val root = rule.activity.window.decorView
        val b = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(b))
        File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tap(text: String, ms: Long = 2500, exact: Boolean = false) {
        settle(600)
        rule.onAllNodesWithText(text, substring = !exact, useUnmergedTree = exact)[0].performClick()
        settle(ms)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
    }

    private fun back() {
        rule.onAllNodesWithContentDescription("Back")[0].performClick()
        settle(1500)
    }

    @Test
    fun screens() {
        val app = ApplicationProvider.getApplicationContext<SilverboxApp>()
        runBlocking {
            DemoSeed.seedIfEmpty(app, app.container) { name -> File(photos, name.removePrefix("demo/")).inputStream() }
            app.container.lab.work()
        }
        rule.setContent {
            val s: Settings? by app.container.settings.settings.collectAsState(initial = null)
            SilverboxTheme(dark = System.getProperty("render.dark") == "true") { s?.let { SilverboxRoot(it) } }
        }
        settle(5000)
        shot("1-camera")
        tap("Open the envelope")
        shot("2-envelope")
        tap("Open", 3000)
        shot("3-stack")
        back()
        tap("Album", exact = true)
        shot("4-album")
        tap("Shoebox", exact = true)
        shot("5-shoebox")
        tap("Shelf", exact = true)
        shot("6-shelf")
        tap("Summer at Gran")
        shot("7-roll")
        tap("Negatives", exact = true)
        shot("8-negatives")
        back()
        tap("Camera", exact = true)
        tap("To the lab")
        shot("9-receipt")
    }
}
