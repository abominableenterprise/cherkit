package com.abominableenterprise.cherkit.whatsnew.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertTrue

/**
 * Renders the sheet and history with Robolectric's native graphics into `build/outputs/whatsnew-screenshots/` (for
 * review and PR screenshots; not compared against goldens). Each test checks the capture is not a blank frame. The
 * sheet's content is drawn in a sheet-shaped surface over a dimmed app, as the bottom sheet window would show it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xhdpi")
class WhatsNewScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sheetLight() = sheet(dark = false, "sheet-light")

    @Test
    fun sheetDark() = sheet(dark = true, "sheet-dark")

    @Test
    fun historyLight() = history(dark = false, "history-light")

    @Test
    fun historyDark() = history(dark = true, "history-dark")

    @Test
    fun historyWithTitleLight() = history(dark = false, "history-title-light", showTitle = true)

    @Test
    fun historyWithTitleDark() = history(dark = true, "history-title-dark", showTitle = true)

    private fun sheet(dark: Boolean, name: String) {
        compose.setContent {
            WhatsNewPreviewTheme(dark) {
                Box(Modifier.fillMaxSize()) {
                    Text("Your app", Modifier.padding(24.dp))
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)))
                    Surface(
                        Modifier.align(Alignment.BottomCenter).padding(top = 48.dp),
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        WhatsNewSheetContent(
                            WhatsNewPreviewData.entries(2),
                            WhatsNewStrings(),
                            onContinue = {},
                            Modifier.padding(top = 24.dp),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        save(draw(compose.activity.window.decorView), name)
    }

    private fun history(dark: Boolean, name: String, showTitle: Boolean = false) {
        compose.setContent {
            WhatsNewPreviewTheme(dark) { WhatsNewHistoryScreen(WhatsNewPreviewData.history, showTitle = showTitle) }
        }
        compose.waitForIdle()
        save(draw(compose.activity.window.decorView), name)
    }

    private fun draw(view: View): Bitmap =
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }

    private fun save(bitmap: Bitmap, name: String) {
        val colors = buildSet {
            for (y in 0 until bitmap.height step 8) for (x in 0 until bitmap.width step 8) add(bitmap.getPixel(x, y))
        }
        assertTrue(colors.size > 2, "$name rendered a blank frame")
        val dir = File("build/outputs/whatsnew-screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
