package com.abominableenterprise.cherkit.sunset.compose

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.abominableenterprise.cherkit.core.AppVersion
import com.abominableenterprise.cherkit.sunset.SunsetPrompt

internal object SunsetPreviewData {
    private const val STORE_URL = "https://play.google.com/store/apps/details?id=com.example.app"
    val prompt = SunsetPrompt(STORE_URL, message = null, latestVersion = AppVersion.parse("2.0"))
    val longPrompt = SunsetPrompt(
        STORE_URL,
        message = "We rebuilt syncing from the ground up, so your data now stays in step across all your devices. " +
            "This version can no longer talk to the new sync service. Updating takes a minute, and everything " +
            "you have saved stays right where it is. Thanks for sticking with us!",
    )
}

/** Material 3 light or dark, following the system, as a stand-in for the app's own theme. */
@Composable
internal fun SunsetPreviewTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme(), content = content)
}

@Preview(name = "Soft prompt")
@Preview(name = "Soft prompt, dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SoftPromptPreview() {
    SunsetPreviewTheme { SunsetSoftPromptDialog(SunsetPreviewData.prompt, onUpdate = {}, onNotNow = {}) }
}

@Preview(name = "Hard block", showSystemUi = true)
@Preview(name = "Hard block, dark", showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HardBlockPreview() {
    SunsetPreviewTheme { SunsetUpdateScreen(SunsetPreviewData.prompt) }
}

@Preview(name = "Long message, largest font", showSystemUi = true, fontScale = 2f)
@Composable
private fun HardBlockLargestFontPreview() {
    SunsetPreviewTheme { SunsetUpdateScreen(SunsetPreviewData.longPrompt) }
}
