package com.abominableenterprise.cherkit.sunset.compose

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import com.abominableenterprise.cherkit.core.AppVersion
import com.abominableenterprise.cherkit.sunset.SunsetDecision
import com.abominableenterprise.cherkit.sunset.SunsetPrompt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Renders [SunsetPrompt] with Robolectric: what each decision shows and what its buttons do. */
@RunWith(RobolectricTestRunner::class)
class SunsetPromptUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val storeUrl = "https://play.google.com/store/apps/details?id=com.example.app"
    private val prompt = SunsetPrompt(storeUrl, message = null, latestVersion = AppVersion.parse("2.0"))
    private val strings = SunsetStrings()
    private val opened = mutableListOf<String>()
    private var handled = 0
    private lateinit var memory: SunsetSoftPromptMemory

    @Before
    fun setUp() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("sunset-ui-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        memory = SunsetSoftPromptMemory(prefs)
    }

    private fun show(decision: SunsetDecision?) {
        var current by mutableStateOf(decision)
        compose.setContent {
            val uriHandler = object : UriHandler {
                override fun openUri(uri: String) {
                    opened += uri
                }
            }
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                SunsetPrompt(current, onDecisionHandled = { handled++; current = null }, memory = memory)
            }
        }
    }

    @Test
    fun softShowsUpdateAndNotNow() {
        show(SunsetDecision.SoftUpdate(prompt))
        compose.onNodeWithText(strings.softTitle).assertIsDisplayed().assert(isHeading)
        compose.onNodeWithText(strings.softMessage).assertIsDisplayed()
        compose.onNodeWithText(strings.updateButton).assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText(strings.notNowButton).assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun notNowIsRememberedForTheLatestVersion() {
        show(SunsetDecision.SoftUpdate(prompt))
        compose.onNodeWithText(strings.notNowButton).performClick()
        compose.waitForIdle()
        assertEquals("2.0", memory.dismissedVersion)
        assertEquals(1, handled)
        assertEquals(emptyList(), opened)
        compose.onNodeWithText(strings.softTitle).assertDoesNotExist()
    }

    @Test
    fun backOnTheSoftPromptCountsAsNotNow() {
        show(SunsetDecision.SoftUpdate(prompt))
        compose.onNodeWithText(strings.softTitle).assertIsDisplayed()
        Espresso.pressBack()
        compose.waitForIdle()
        assertEquals("2.0", memory.dismissedVersion)
        compose.onNodeWithText(strings.softTitle).assertDoesNotExist()
    }

    @Test
    fun softIsNotShownAgainForTheDismissedVersion() {
        memory.rememberNotNow("2.0")
        show(SunsetDecision.SoftUpdate(prompt))
        compose.onNodeWithText(strings.softTitle).assertDoesNotExist()
        assertEquals(0, handled)
    }

    @Test
    fun updateOpensTheStoreWithoutRememberingNotNow() {
        show(SunsetDecision.SoftUpdate(prompt))
        compose.onNodeWithText(strings.updateButton).performClick()
        compose.waitForIdle()
        assertEquals(listOf(storeUrl), opened)
        assertNull(memory.dismissedVersion)
        assertEquals(1, handled)
    }

    @Test
    fun hardShowsTheBlockAndUpdateOpensTheStore() {
        show(SunsetDecision.HardUpdate(SunsetPrompt(storeUrl, "Please update from Google Play.")))
        compose.onNodeWithText(strings.hardTitle).assertIsDisplayed().assert(isHeading)
        compose.onNodeWithText("Please update from Google Play.").assertIsDisplayed()
        compose.onNodeWithText(strings.notNowButton).assertDoesNotExist()
        compose.onNodeWithText(strings.updateButton).assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(listOf(storeUrl), opened)
        // The block stays after "Update": the app is still the unsupported version.
        compose.onNodeWithText(strings.hardTitle).assertIsDisplayed()
        assertEquals(0, handled)
    }

    @Test
    fun backDoesNotDismissTheHardBlock() {
        show(SunsetDecision.HardUpdate(prompt))
        compose.onNodeWithText(strings.hardTitle).assertIsDisplayed()
        Espresso.pressBackUnconditionally()
        compose.waitForIdle()
        compose.onNodeWithText(strings.hardTitle).assertIsDisplayed()
        compose.onNodeWithText(strings.updateButton).assertIsDisplayed()
        assertNull(memory.dismissedVersion)
        assertEquals(0, handled)
    }

    @Test
    fun upToDateShowsNothing() {
        show(SunsetDecision.UpToDate)
        assertNothingShown()
    }

    @Test
    fun nullShowsNothing() {
        show(null)
        assertNothingShown()
    }

    private fun assertNothingShown() {
        compose.onNodeWithText(strings.softTitle).assertDoesNotExist()
        compose.onNodeWithText(strings.hardTitle).assertDoesNotExist()
        compose.onNodeWithText(strings.updateButton).assertDoesNotExist()
    }

    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
}
