package com.abominableenterprise.cherkit.update.compose

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class SharedPreferencesUpdatePromptStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun remembersTheDeclinedVersionCodeAcrossInstances() {
        assertNull(AndroidUpdate.promptStore(context).declinedVersionCode)
        AndroidUpdate.promptStore(context).rememberDeclined(42)
        assertEquals(42, AndroidUpdate.promptStore(context).declinedVersionCode)
        AndroidUpdate.promptStore(context).rememberDeclined(0)
        assertEquals(0, AndroidUpdate.promptStore(context).declinedVersionCode)
        AndroidUpdate.promptStore(context).reset()
        assertNull(AndroidUpdate.promptStore(context).declinedVersionCode)
    }

    @Test
    fun aValueOfAnotherTypeCountsAsNever() {
        val prefs = context.getSharedPreferences(AndroidUpdate.DEFAULT_PREFERENCES_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(SharedPreferencesUpdatePromptStore.DEFAULT_KEY, "42").commit()
        assertNull(AndroidUpdate.promptStore(context).declinedVersionCode)
    }
}
