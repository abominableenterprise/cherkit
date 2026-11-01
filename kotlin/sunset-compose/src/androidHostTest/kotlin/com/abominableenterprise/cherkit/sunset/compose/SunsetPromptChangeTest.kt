package com.abominableenterprise.cherkit.sunset.compose

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** `onPromptChange` reports the soft dialog and the hard block; `onVisibilityChange` still reports the soft only. */
@RunWith(RobolectricTestRunner::class)
class SunsetPromptChangeTest {
    @get:Rule
    val compose = createComposeRule()

    private val strings = SunsetStrings()
    private val prompt = SunsetPrompt("https://play.google.com/store/apps/details?id=a", null, AppVersion.parse("2.0"))
    private val soft = SunsetDecision.SoftUpdate(prompt)
    private val hard = SunsetDecision.HardUpdate(prompt)
    private val kinds = mutableListOf<SunsetPromptKind?>()
    private val visibility = mutableListOf<Boolean>()
    private lateinit var memory: SunsetSoftPromptMemory
    private var decision: SunsetDecision? by mutableStateOf(null)
    private var shown by mutableStateOf(true)

    @Before
    fun setUp() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("sunset-kind-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        memory = SunsetSoftPromptMemory(prefs)
        compose.setContent {
            if (shown) {
                SunsetPrompt(
                    decision,
                    onDecisionHandled = { decision = null },
                    memory = memory,
                    onPromptChange = { kinds += it },
                ) { visibility += it }
            }
        }
        compose.waitForIdle()
    }

    private fun set(newDecision: SunsetDecision?) {
        decision = newDecision
        compose.waitForIdle()
    }

    @Test
    fun reportsSoftHardAndNone() {
        assertEquals(emptyList<SunsetPromptKind?>(), kinds, "the initial null is not reported")
        set(soft)
        assertEquals(listOf<SunsetPromptKind?>(SunsetPromptKind.SOFT), kinds)
        compose.onNodeWithText(strings.updateButton).performClick()
        compose.waitForIdle()
        assertEquals(listOf<SunsetPromptKind?>(SunsetPromptKind.SOFT, null), kinds)

        set(hard)
        compose.onNodeWithText(strings.hardTitle).assertExists()
        assertEquals(listOf<SunsetPromptKind?>(SunsetPromptKind.SOFT, null, SunsetPromptKind.HARD), kinds)
        set(SunsetDecision.UpToDate)
        assertEquals(listOf<SunsetPromptKind?>(SunsetPromptKind.SOFT, null, SunsetPromptKind.HARD, null), kinds)
        // onVisibilityChange is unchanged: the soft dialog only.
        assertEquals(listOf(true, false), visibility)
    }

    @Test
    fun softToHardSwitchesWithoutNull() {
        set(soft)
        set(hard)
        assertEquals(listOf<SunsetPromptKind?>(SunsetPromptKind.SOFT, SunsetPromptKind.HARD), kinds)
        assertEquals(listOf(true, false), visibility)
    }

    @Test
    fun aSoftPromptAlreadyAnsweredIsNotReported() {
        memory.rememberNotNow("2.0")
        set(soft)
        assertEquals(emptyList<SunsetPromptKind?>(), kinds)
    }

    @Test
    fun leavingTheCompositionReportsNull() {
        set(hard)
        shown = false
        compose.waitForIdle()
        assertEquals(listOf<SunsetPromptKind?>(SunsetPromptKind.HARD, null), kinds)
        shown = true
        compose.waitForIdle()
        assertEquals(listOf<SunsetPromptKind?>(SunsetPromptKind.HARD, null, SunsetPromptKind.HARD), kinds)
    }
}
