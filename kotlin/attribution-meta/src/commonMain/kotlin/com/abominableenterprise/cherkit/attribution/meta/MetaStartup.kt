package com.abominableenterprise.cherkit.attribution.meta

/** Narrow vendor boundary: deliberately no purchase logging or device/advertiser-ID APIs. */
internal interface MetaSdk {
    fun disableAutomaticFeatures()
    fun initialize()
    fun anonymousId(): String
    fun activateApp()
}

internal interface MetaObservation {
    fun observe(onForeground: () -> Unit, onIdentityChange: () -> Unit)
    fun stop()
}

/** Caller serializes starts and observer callbacks. RevenueCat setup is always delegated first. */
internal class MetaStartup(
    private val meta: MetaSdk,
    private val observation: MetaObservation,
    private val setFBAnonymousID: (String) -> Unit,
) {
    private var running = false

    fun start(allowed: Boolean, configureRevenueCat: () -> Unit) {
        configureRevenueCat()
        if (!allowed) {
            stop()
            return
        }
        if (running) return
        // Optional attribution must never prevent the already-configured purchase flow.
        try {
            meta.disableAutomaticFeatures()
            meta.initialize()
            running = true
            observation.observe({ refresh(activate = true) }, { refresh(activate = false) })
            refresh(activate = true)
        } catch (_: Exception) {
            stop()
        }
    }

    private fun refresh(activate: Boolean) {
        if (!running) return
        try {
            val id = meta.anonymousId()
            if (id.isBlank()) return // Never invent an ID or delay a purchase waiting for one.
            setFBAnonymousID(id)
            if (activate) meta.activateApp()
        } catch (_: Exception) {
            // RevenueCat and Meta own their network work; no custom retry queue or payload logs.
        }
    }

    fun stop() {
        if (!running) return
        running = false
        observation.stop()
    }
}
