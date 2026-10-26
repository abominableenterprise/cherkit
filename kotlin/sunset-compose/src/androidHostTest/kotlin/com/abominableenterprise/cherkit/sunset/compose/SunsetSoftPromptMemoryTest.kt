package com.abominableenterprise.cherkit.sunset.compose

import android.content.Context
import android.content.SharedPreferences
import com.abominableenterprise.cherkit.sunset.AndroidSunset
import com.abominableenterprise.cherkit.sunset.SunsetDecision
import com.abominableenterprise.cherkit.sunset.SunsetPrompt
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [SunsetSoftPromptMemory] on Robolectric's real SharedPreferences. */
@RunWith(RobolectricTestRunner::class)
class SunsetSoftPromptMemoryTest {
    private val soft = SunsetDecision.SoftUpdate(SunsetPrompt("https://play.google.com/store/apps/details?id=a", null))
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs = context.getSharedPreferences("sunset-compose-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun remembersNotNowPerVersion() {
        val memory = SunsetSoftPromptMemory(prefs)
        assertNull(memory.dismissedVersion)
        assertTrue(memory.shouldShowSoftPrompt(soft, "2.0"))

        memory.rememberNotNow("2.0")
        assertEquals("2.0", memory.dismissedVersion)
        assertFalse(memory.shouldShowSoftPrompt(soft, "2.0"))
        assertTrue(memory.shouldShowSoftPrompt(soft, "2.1"))
    }

    @Test
    fun survivesANewInstanceLikeANextLaunch() {
        SunsetSoftPromptMemory(prefs).rememberNotNow("2.0")
        assertFalse(SunsetSoftPromptMemory(prefs).shouldShowSoftPrompt(soft, "2.0"))
    }

    @Test
    fun usesTheInjectedKey() {
        SunsetSoftPromptMemory(prefs, "custom.key").rememberNotNow("3.0")
        assertEquals("3.0", prefs.getString("custom.key", null))
        assertNull(prefs.getString(SunsetSoftPromptMemory.DEFAULT_KEY, null))
        assertNull(SunsetSoftPromptMemory(prefs).dismissedVersion)
    }

    @Test
    fun resetShowsThePromptAgain() {
        val memory = SunsetSoftPromptMemory(prefs)
        memory.rememberNotNow("2.0")
        memory.reset()
        assertNull(memory.dismissedVersion)
        assertTrue(memory.shouldShowSoftPrompt(soft, "2.0"))
    }

    @Test
    fun anotherTypeUnderTheKeyCountsAsNeverDismissed() {
        prefs.edit().putInt(SunsetSoftPromptMemory.DEFAULT_KEY, 42).commit()
        val memory = SunsetSoftPromptMemory(prefs)
        assertNull(memory.dismissedVersion)
        assertTrue(memory.shouldShowSoftPrompt(soft, "2.0"))
        memory.rememberNotNow("2.0")
        assertEquals("2.0", memory.dismissedVersion)
    }

    @Test
    fun fromUsesTheSunsetPreferencesFileByDefault() {
        SunsetSoftPromptMemory.from(context).rememberNotNow("4.0")
        val file = context.getSharedPreferences(AndroidSunset.DEFAULT_PREFERENCES_NAME, Context.MODE_PRIVATE)
        assertEquals("4.0", file.getString(SunsetSoftPromptMemory.DEFAULT_KEY, null))
    }
}
