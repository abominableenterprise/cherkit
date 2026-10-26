package com.abominableenterprise.cherkit.sunset.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Renders the previews with Robolectric's native graphics into `build/outputs/sunset-screenshots/` (for review and PR
 * screenshots; not compared against goldens). Each test checks the capture is not a blank frame. The windows are
 * drawn straight onto a canvas: `captureToImage()` waits for a PixelCopy frame that never comes under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xhdpi")
class SunsetScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun softLight() = soft(dark = false, "soft-light")

    @Test
    fun softDark() = soft(dark = true, "soft-dark")

    @Test
    fun hardLight() = hard(dark = false, "hard-light")

    @Test
    fun hardDark() = hard(dark = true, "hard-dark")

    private fun soft(dark: Boolean, name: String) {
        compose.setContent {
            SunsetPreviewTheme(dark) {
                Text("Your app", Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp))
                SunsetSoftPromptDialog(SunsetPreviewData.prompt, onUpdate = {}, onNotNow = {})
            }
        }
        compose.waitForIdle()
        val dialog = assertNotNull(ShadowDialog.getLatestDialog(), "no dialog shown")
        val screen = draw(compose.activity.window.decorView)
        val window = dialog.window!!.decorView
        // The dialog window over a dimmed activity, centered like on a device.
        Canvas(screen).apply {
            drawColor(0x99000000.toInt())
            val content = draw(window)
            drawBitmap(content, (width - content.width) / 2f, (height - content.height) / 2f, null)
        }
        save(screen, name)
    }

    private fun hard(dark: Boolean, name: String) {
        compose.setContent { SunsetPreviewTheme(dark) { SunsetUpdateScreen(SunsetPreviewData.prompt) } }
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
        val dir = File("build/outputs/sunset-screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
