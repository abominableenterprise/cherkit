package com.abominableenterprise.cherkit.attribution.meta

import kotlin.test.Test
import kotlin.test.assertEquals

class MetaStartupTest {
    private class Recording {
        val calls = mutableListOf<String>()
        var id = "actual-meta-id"
        var failInit = false
        var foreground: (() -> Unit)? = null
        var identity: (() -> Unit)? = null
        var registrations = 0
        val meta = object : MetaSdk {
            override fun disableAutomaticFeatures() { calls += "all-auto-features-off" }
            override fun initialize() {
                calls += "initialize"
                if (failInit) error("unavailable")
            }
            override fun anonymousId(): String { calls += "read-id"; return id }
            override fun activateApp() { calls += "activate" }
        }
        val observer = object : MetaObservation {
            override fun observe(onForeground: () -> Unit, onIdentityChange: () -> Unit) {
                registrations++
                calls += "observe"
                foreground = onForeground
                identity = onIdentityChange
            }
            override fun stop() { calls += "remove-observer"; foreground = null; identity = null }
        }
        val startup = MetaStartup(meta, observer) { calls += "setFBAnonymousID:$it" }
        fun start(allowed: Boolean = true) = startup.start(allowed) { calls += "safe-RC-adapter" }
    }

    @Test fun configuresRevenueCatThenFlagsThenMetaThenRealIdBeforeActivation() {
        val r = Recording()
        r.start()
        assertEquals(listOf("safe-RC-adapter", "all-auto-features-off", "initialize", "observe", "read-id",
            "setFBAnonymousID:actual-meta-id", "activate"), r.calls)
    }

    @Test fun repeatedStartHasOneObserverAndNoDuplicateActivation() {
        val r = Recording()
        repeat(3) { r.start() }
        assertEquals(1, r.registrations)
        assertEquals(1, r.calls.count { it == "initialize" })
        assertEquals(1, r.calls.count { it == "activate" })
        assertEquals(3, r.calls.count { it == "safe-RC-adapter" })
    }

    @Test fun foregroundAndIdentityRefreshActualIdWithoutConfiguringAgain() {
        val r = Recording()
        r.start()
        r.calls.clear()
        r.id = "new-meta-id"
        r.identity?.invoke()
        r.foreground?.invoke()
        assertEquals(listOf("read-id", "setFBAnonymousID:new-meta-id", "read-id",
            "setFBAnonymousID:new-meta-id", "activate"), r.calls)
    }

    @Test fun ineligibleStartIsExactlyRevenueCatOnly() {
        val r = Recording()
        repeat(3) { r.start(false) }
        assertEquals(List(3) { "safe-RC-adapter" }, r.calls)
    }

    @Test fun stopAndIneligibleRestartRemoveObserverAndIgnoreAlreadyQueuedCallbacks() {
        val r = Recording()
        r.start()
        val staleForeground = r.foreground
        val staleIdentity = r.identity
        r.calls.clear()
        r.start(false)
        r.startup.stop()
        staleForeground?.invoke()
        staleIdentity?.invoke()
        assertEquals(listOf("safe-RC-adapter", "remove-observer"), r.calls)
        r.start()
        assertEquals(2, r.registrations)
    }

    @Test fun initFailureDoesNotBlockPurchasesAndCanRetry() {
        val r = Recording()
        r.failInit = true
        r.start()
        assertEquals(listOf("safe-RC-adapter", "all-auto-features-off", "initialize"), r.calls)
        r.failInit = false
        r.start()
        assertEquals(1, r.registrations)
    }

    @Test fun missingIdIsNotInventedAndDoesNotActivateButNextForegroundRetries() {
        val r = Recording()
        r.id = " "
        r.start()
        assertEquals(0, r.calls.count { it.startsWith("setFBAnonymousID") || it == "activate" })
        r.id = "real-id"
        r.foreground?.invoke()
        assertEquals(listOf("read-id", "setFBAnonymousID:real-id", "activate"), r.calls.takeLast(3))
    }

    @Test fun failedIdentifierWriteNeverActivatesAndLeavesPurchasesAvailable() {
        val r = Recording()
        val startup = MetaStartup(r.meta, r.observer) { error("identifier unavailable") }
        startup.start(true) { r.calls += "safe-RC-adapter" }
        assertEquals(0, r.calls.count { it == "activate" })
        assertEquals(1, r.registrations)
    }
}
