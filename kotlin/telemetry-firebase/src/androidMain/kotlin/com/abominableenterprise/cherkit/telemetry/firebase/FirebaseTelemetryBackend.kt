package com.abominableenterprise.cherkit.telemetry.firebase

import com.abominableenterprise.cherkit.telemetry.TelemetryBackend
import com.google.firebase.crashlytics.CustomKeysAndValues
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance

/**
 * Android Firebase adapter. Use one instance on the main thread, through Telemetry.
 * Collection is disabled at construction. Performance is opt-in because Firebase also collects
 * automatic app/screen traces. Disable network instrumentation in the app's Gradle configuration.
 * Firebase must already be configured by the app. SDK failures, including missing SDKs, are ignored.
 */
public class FirebaseTelemetryBackend private constructor(
    backend: FirebaseBackend,
) : TelemetryBackend by backend {
    public constructor(performanceEnabled: Boolean = false) :
        this(FirebaseBackend(AndroidFirebaseClient(), performanceEnabled))
}

internal class AndroidFirebaseClient : FirebaseClient {
    override fun crashCollection(enabled: Boolean) {
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
    }
    override fun performanceCollection(enabled: Boolean) {
        FirebasePerformance.getInstance().isPerformanceCollectionEnabled = enabled
    }
    override fun error(type: String, domain: String?, code: Int?, attributes: Map<String, String>) {
        val keys = CustomKeysAndValues.Builder()
            .putString("cherkit.error.type", type)
        domain?.let { keys.putString("cherkit.error.domain", it) }
        code?.let { keys.putInt("cherkit.error.code", it) }
        attributes.filterKeys { !it.startsWith("cherkit.error.") }
            .forEach { (key, value) -> keys.putString(key, value) }
        // The core deliberately carries no original stack or message. Do not invent an app stack.
        val error = SanitizedTelemetryException().apply { stackTrace = emptyArray() }
        FirebaseCrashlytics.getInstance().recordException(error, keys.build())
    }
    override fun log(message: String) { FirebaseCrashlytics.getInstance().log(message) }
    override fun key(name: String, value: String) { FirebaseCrashlytics.getInstance().setCustomKey(name, value) }
    override fun newTrace(name: String): FirebaseTrace {
        val trace = FirebasePerformance.getInstance().newTrace(name)
        return object : FirebaseTrace {
            override fun start() { trace.start() }
            override fun stop() { trace.stop() }
            override fun attribute(name: String, value: String) { trace.putAttribute(name, value) }
        }
    }
}

internal class SanitizedTelemetryException : Exception(null, null, false, false)
