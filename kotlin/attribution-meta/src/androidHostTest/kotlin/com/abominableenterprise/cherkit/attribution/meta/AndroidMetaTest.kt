package com.abominableenterprise.cherkit.attribution.meta

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.abominableenterprise.cherkit.attribution.revenuecat.Audience
import com.facebook.FacebookSdk
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AndroidMetaTest {
    @Test fun nativeFlagsAreOffBeforeInitializationIncludingAutomaticEvents() {
        assertFalse(FacebookSdk.isInitialized())
        AndroidMetaSdk(RuntimeEnvironment.getApplication()).disableAutomaticFeatures()
        assertFalse(FacebookSdk.isInitialized())
        assertFalse(FacebookSdk.getAutoInitEnabled())
        assertFalse(FacebookSdk.getAutoLogAppEventsEnabled())
        assertFalse(FacebookSdk.getAdvertiserIDCollectionEnabled())
        assertFalse(FacebookSdk.getAutoLogMetaDataEnabled())
        assertFalse(FacebookSdk.getAutoLogProductImprovementEnabled())
    }

    @Test fun observerRefreshesPinnedIdentityKeyAndForegroundWithoutDuplicatesOrAttributeLoops() {
        val application = RuntimeEnvironment.getApplication()
        val calls = mutableListOf<String>()
        val observer = MetaObserver(application, application, "public_key") { it() }
        repeat(3) { observer.observe({ calls += "foreground" }, { calls += "identity" }) }
        val activity = Robolectric.buildActivity(Activity::class.java).create().start().resume()
        assertEquals(listOf("foreground"), calls)
        val prefs = application.getSharedPreferences("com_revenuecat_purchases_preferences", Context.MODE_PRIVATE)
        prefs.edit().putString("com.revenuecat.purchases.public_key.new", "logged-in-user").commit()
        prefs.edit().putString("com.revenuecat.purchases.public_key.subscriberAttributes", "ignored").commit()
        prefs.edit().putString("com.revenuecat.purchases.other_key.new", "unrelated-user").commit()
        prefs.edit().remove("com.revenuecat.purchases.public_key.new").commit()
        assertEquals(listOf("foreground", "identity"), calls)
        activity.pause().stop()
        activity.start().resume()
        assertEquals(listOf("foreground", "identity", "foreground"), calls)
        observer.stop()
        observer.stop()
        prefs.edit().putString("com.revenuecat.purchases.public_key.new", "anonymous-user").commit()
        activity.pause().stop().start().resume()
        assertEquals(listOf("foreground", "identity", "foreground"), calls)
        activity.pause().stop().destroy()
    }

    @Test fun observerDoesNotTreatSecondActivityAsAnotherForeground() {
        val application = RuntimeEnvironment.getApplication()
        var foregrounds = 0
        val observer = MetaObserver(application, application, "public_key") { it() }
        observer.observe({ foregrounds++ }, {})
        val first = Robolectric.buildActivity(Activity::class.java).create().start().resume()
        val second = Robolectric.buildActivity(Activity::class.java).create().start().resume()
        assertEquals(1, foregrounds)
        second.pause().stop().destroy()
        first.pause().stop().destroy()
        observer.stop()
    }

    @Test fun bundledConfigRequiresNonblankStringsAndEveryExplicitFalseFlag() {
        val application = RuntimeEnvironment.getApplication()
        val flags = listOf("AutoInitEnabled", "AutoLogAppEventsEnabled", "AdvertiserIDCollectionEnabled",
            "AutoLogMetaDataEnabled", "AutoLogProductImprovementEnabled")
        val metadata = Bundle().apply {
            putString("com.facebook.sdk.ApplicationId", "public-app-id")
            putString("com.facebook.sdk.ClientToken", "public-client-token")
            flags.forEach { putBoolean("com.facebook.sdk.$it", false) }
        }
        assertTrue(MetaLaunchPolicy.hasConfiguration(application, metadata))
        flags.forEach { flag ->
            val key = "com.facebook.sdk.$flag"
            metadata.putBoolean(key, true)
            assertFalse(MetaLaunchPolicy.hasConfiguration(application, metadata))
            metadata.remove(key)
            assertFalse(MetaLaunchPolicy.hasConfiguration(application, metadata))
            metadata.putBoolean(key, false)
        }
        metadata.putString("com.facebook.sdk.ClientToken", " ")
        assertFalse(MetaLaunchPolicy.hasConfiguration(application, metadata))
        metadata.putInt("com.facebook.sdk.ApplicationId", 12345)
        assertFalse(MetaLaunchPolicy.hasConfiguration(application, metadata))
    }

    @Test fun testHostNeverEnablesMetaAndMissingConfigFailsClosed() {
        val application = RuntimeEnvironment.getApplication()
        assertFalse(MetaLaunchPolicy.allowed(application, Audience.AdultOnly))
        assertFalse(MetaLaunchPolicy.allowed(application, Audience.RestrictedOrSwitchable))
        assertTrue(!FacebookSdk.isInitialized())
    }
}
