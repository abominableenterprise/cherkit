package com.abominableenterprise.cherkit.whatsnew

import android.os.Looper
import com.abominableenterprise.cherkit.core.TelemetrySink
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.concurrent.thread
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class WhatsNewTelemetryThreadTest {
    @Test
    fun ioLoadingReportsOnTheMainThreadInOrder() {
        val main = Looper.getMainLooper().thread
        val reports = mutableListOf<String>()
        val sink = object : TelemetrySink {
            override fun recordError(domain: String, code: Int, attributes: Map<String, Any>) {
                assertEquals(main, Thread.currentThread())
                assertEquals(emptyMap(), attributes)
                reports += "$domain.$code"
            }
            override fun breadcrumb(name: String, attributes: Map<String, Any>) {
                assertEquals(main, Thread.currentThread())
                reports += name
            }
        }
        val store = KeyValueWhatsNewStore(object : KeyValueStorage {
            private val values = mutableMapOf<String, String>()
            override fun getString(key: String): String? = values[key]
            override fun putString(key: String, value: String) { values[key] = value }
        }).also { it.setLastSeenVersion("1") }
        val checker = WhatsNewChecker(LoadedReleaseNotesSource {
            listOf("bad.json" to "private message", "2.json" to """{"version":"2","notes":{"en":["New tools."]}}""")
        }, store, sink)
        thread {
            checker.onLaunch("2", listOf("fr"))
            checker.markSeen()
        }.join()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("cherkit.whatsnew.2", "cherkit.whatsnew.3", "cherkit.whatsnew.shown"), reports)
    }
}
