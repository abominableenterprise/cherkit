package com.abominableenterprise.cherkit.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class PostHogAnalyticsBackendTest {
    private val fakes = FakePostHogClientFactory()
    private val backend = PostHogAnalyticsBackend("phc_test", "https://eu.i.posthog.com", fakes.factory)

    @Test
    fun nothingIsCreatedBeforeStart() {
        PostHogAnalyticsBackend("phc_test", "https://eu.i.posthog.com", fakes.factory)
        assertTrue(fakes.clients.isEmpty())
    }

    @Test
    fun startCreatesTheClientLazilyThenOptsIn() {
        backend.start()
        assertEquals(1, fakes.clients.size)
        assertEquals(listOf("optIn"), fakes.clients[0].methods)
        assertEquals("phc_test", fakes.configs[0].apiKey)
        assertEquals("https://eu.i.posthog.com", fakes.configs[0].host)
    }

    @Test
    fun captureSendsOnlyTheNameNeverProperties() {
        backend.start()
        backend.capture("app_opened")
        backend.capture("item_added")

        val captures = fakes.clients[0].calls.filter { it.method == "capture" }
        assertEquals(listOf("app_opened", "item_added"), captures.map { it.args[0] })
        for (call in captures) {
            // distinctId, properties, userProperties, userPropertiesSetOnce, groups, timestamp: all unset.
            assertEquals(6, call.args.size - 1, "capture signature changed: ${call.args}")
            assertTrue(call.args.drop(1).all { it == null }, "capture passed more than a name: ${call.args}")
        }
    }

    @Test
    fun stopOptsOutThenClosesAndOnlyThoseCallsAreEverMade() {
        backend.start()
        backend.capture("app_opened")
        backend.stop()

        // The full call list: no identify, alias, group, register, person properties or anything else.
        assertEquals(listOf("optIn", "capture", "optOut", "close"), fakes.clients[0].methods)
    }

    @Test
    fun restartCreatesAFreshClient() {
        backend.start()
        backend.stop()
        backend.start()
        backend.capture("app_opened")

        assertEquals(2, fakes.clients.size)
        assertNotSame(fakes.clients[0].client, fakes.clients[1].client)
        assertEquals(listOf("optIn", "optOut", "close"), fakes.clients[0].methods)
        assertEquals(listOf("optIn", "capture"), fakes.clients[1].methods)
    }

    @Test
    fun startTwiceReusesTheClient() {
        backend.start()
        backend.start()
        assertEquals(1, fakes.clients.size)
        assertEquals(listOf("optIn", "optIn"), fakes.clients[0].methods)
    }

    @Test
    fun stopWithoutStartDoesNothing() {
        backend.stop()
        assertTrue(fakes.clients.isEmpty())
    }

    @Test
    fun stopClosesAndDropsTheClientEvenIfOptOutThrows() {
        backend.start()
        fakes.clients[0].failNext += "optOut"
        assertFailsWith<IllegalStateException> { backend.stop() }
        assertEquals(listOf("optIn", "optOut", "close"), fakes.clients[0].methods)

        backend.start()
        assertEquals(2, fakes.clients.size)
    }

    @Test
    fun captureWithoutStartThrowsInsteadOfCreatingAClient() {
        assertFailsWith<IllegalStateException> { backend.capture("app_opened") }
        assertTrue(fakes.clients.isEmpty())
    }

    @Test
    fun blankKeyThrowsIllegalArgumentWithoutCreatingAClient() {
        for (key in listOf("", "   ")) {
            val blank = PostHogAnalyticsBackend(key, PostHogAnalyticsBackend.DEFAULT_HOST, fakes.factory)
            assertFailsWith<IllegalArgumentException> { blank.start() }
        }
        assertTrue(fakes.clients.isEmpty())
    }

    @Test
    fun defaultHostIsPostHogUsCloud() {
        assertEquals("https://us.i.posthog.com", PostHogAnalyticsBackend.DEFAULT_HOST)
    }
}
