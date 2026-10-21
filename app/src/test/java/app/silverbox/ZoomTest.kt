package app.silverbox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import app.silverbox.ui.print.Zoom
import app.silverbox.ui.print.zoomable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Looking at a print up close: the real gestures on the real modifier. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZoomTest {
    @get:Rule
    val rule = createComposeRule()

    private val zoom = Zoom()
    private var turned = 0

    private fun print() {
        rule.setContent { Box(Modifier.size(300.dp).testTag("print").zoomable(zoom) { turned++ }) }
    }

    @Test
    fun aDoubleTapGoesCloseAndBack() {
        print()
        rule.onNodeWithTag("print").performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        assertEquals(Zoom.TAP, zoom.scale, 0.01f)
        rule.onNodeWithTag("print").performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        assertEquals(1f, zoom.scale, 0.01f)
        assertEquals(0, turned)
    }

    @Test
    fun aTapStillTurnsThePrintButNotWhileClose() {
        print()
        rule.onNodeWithTag("print").performTouchInput { click(center) }
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        assertEquals(1, turned)
        rule.onNodeWithTag("print").performTouchInput { doubleClick(center) }
        rule.waitForIdle()
        rule.onNodeWithTag("print").performTouchInput { click(center) }
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        assertEquals(1, turned)
    }

    @Test
    fun twoFingersPinchCloseAndOneMovesRound() {
        print()
        rule.onNodeWithTag("print").performTouchInput {
            pinch(center - androidx.compose.ui.geometry.Offset(20f, 0f), centerLeft, center + androidx.compose.ui.geometry.Offset(20f, 0f), centerRight)
        }
        rule.waitForIdle()
        assertTrue("pinched to ${zoom.scale}", zoom.scale > 2f)
        val before = zoom.offset
        rule.onNodeWithTag("print").performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(80f, 60f)) }
        rule.waitForIdle()
        assertNotEquals(before, zoom.offset)
    }

    @Test
    fun atItsOwnSizeOneFingerIsLeftAlone() {
        print()
        rule.onNodeWithTag("print").performTouchInput { swipe(centerRight, centerLeft) }
        rule.waitForIdle()
        assertEquals(1f, zoom.scale, 0f)
        assertEquals(androidx.compose.ui.geometry.Offset.Zero, zoom.offset)
    }
}
