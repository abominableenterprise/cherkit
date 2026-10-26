package com.abominableenterprise.cherkit.sunset.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.abominableenterprise.cherkit.sunset.SunsetPrompt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [SunsetUpdateScreen] on a small screen at a large font scale: the text scrolls, the button stays reachable. */
@RunWith(RobolectricTestRunner::class)
class SunsetUpdateScreenLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val strings = SunsetStrings()
    private val longMessage = List(12) { "This version can no longer talk to the new sync service." }.joinToString(" ")

    @Test
    fun updateButtonStaysVisibleWhileTheMessageScrollsAtTheLargestFontScale() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                Box(Modifier.size(320.dp, 480.dp)) {
                    SunsetUpdateScreen(
                        SunsetPrompt("https://play.google.com/store/apps/details?id=a", longMessage),
                        appIcon = ColorPainter(Color.Red),
                    )
                }
            }
        }
        // Pinned below the scrolling text, so it is on screen without scrolling.
        compose.onNodeWithText(strings.updateButton).assertIsDisplayed()
        // The message is taller than the screen, but it can be scrolled to.
        compose.onNodeWithText(longMessage).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(strings.updateButton).assertIsDisplayed()
    }

    @Test
    fun theIconIsDecorative() {
        compose.setContent {
            SunsetUpdateScreen(SunsetPrompt("https://play.google.com/store/apps/details?id=a", null), appIcon = ColorPainter(Color.Red))
        }
        val tree = compose.onRoot(useUnmergedTree = true).printToString()
        assertFalse(tree.contains(SemanticsProperties.ContentDescription.name), tree)
        assertTrue(tree.contains(strings.hardTitle), tree)
    }
}
