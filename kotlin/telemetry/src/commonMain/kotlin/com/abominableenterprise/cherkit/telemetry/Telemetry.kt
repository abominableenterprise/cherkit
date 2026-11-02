package com.abominableenterprise.cherkit.telemetry

import com.abominableenterprise.cherkit.core.TelemetrySink

/**
 * Diagnostics facade. Enabled by default; restricted sessions may report errors only.
 * Pass buildEnabled=false for debug builds, tests and emulators. No backend starts at construction.
 * Like Analytics, instances and their spans must be used from one thread (normally the main thread).
 * Backend exceptions never escape into app code. Only explicitly allowlisted attributes are sent.
 */
public class Telemetry(
    private val backend: TelemetryBackend,
    public val buildEnabled: Boolean,
) : TelemetrySink {
    private var backendStarted = false
    private var generation = 0L
    private val traces = mutableMapOf<Span, String>()

    public var enabled: Boolean = true
        set(value) {
            if (field != value) invalidateSpans()
            field = value
            syncBackend()
        }

    public var restricted: Boolean = false
        set(value) {
            // Finish while diagnostics are still permitted, before storing a restrictive gate.
            if (field != value) invalidateSpans()
            field = value
        }

    /** Restriction does not disable error reporting. */
    public val isDisabled: Boolean
        get() = !buildEnabled || !enabled

    private val diagnosticsAllowed: Boolean
        get() = !isDisabled && !restricted

    /** Sends only the class name; never passes the Throwable, message, cause, or stack to a provider. */
    public fun recordError(error: Throwable, attributes: Map<String, Any> = emptyMap()) {
        sendError(TelemetryError(type = error::class.simpleName ?: "Throwable"), attributes)
    }

    override fun recordError(domain: String, code: Int, attributes: Map<String, Any>) {
        sendError(TelemetryError(type = "Error", domain = domain, code = code), attributes)
    }

    private fun sendError(error: TelemetryError, attributes: Map<String, Any>) {
        if (isDisabled || !ensureStarted()) return
        bestEffort { backend.recordError(error, TelemetryValidation.attributes(attributes)) }
    }

    override fun breadcrumb(name: String, attributes: Map<String, Any>) {
        if (!diagnosticsAllowed || !TelemetryValidation.validName(name) || !ensureStarted()) return
        bestEffort { backend.breadcrumb(name, TelemetryValidation.attributes(attributes)) }
    }

    public fun setKey(key: String, value: Any) {
        if (!diagnosticsAllowed || !TelemetryValidation.validName(key) ||
            !TelemetryValidation.validValue(value) || !ensureStarted()
        ) return
        bestEffort { backend.setKey(key, value) }
    }

    public fun startSpan(name: String, attributes: Map<String, Any> = emptyMap()): Span {
        if (!diagnosticsAllowed || !TelemetryValidation.validName(name) || !ensureStarted()) return Span()
        val id = try {
            backend.startTrace(name, TelemetryValidation.attributes(attributes))
        } catch (_: Throwable) {
            return Span()
        }
        val startedGeneration = generation
        lateinit var span: Span
        span = Span(
            attribute = { key, value ->
                if (diagnosticsAllowed && generation == startedGeneration && traces.containsKey(span) &&
                    TelemetryValidation.validName(key) && TelemetryValidation.validValue(value)
                ) bestEffort { backend.setAttribute(id, key, value) }
            },
            finish = { status ->
                if (traces.remove(span) != null && diagnosticsAllowed && generation == startedGeneration) {
                    bestEffort { backend.stopTrace(id, status) }
                }
            },
        )
        traces[span] = id
        return span
    }

    /** Always executes [block], even when disabled. Application exceptions are rethrown unchanged. */
    public suspend fun <T> span(
        name: String,
        attributes: Map<String, Any> = emptyMap(),
        block: suspend () -> T,
    ): T {
        val span = startSpan(name, attributes)
        try {
            val result = block()
            span.end()
            return result
        } catch (error: Throwable) {
            span.end(SpanStatus.ERROR)
            throw error
        }
    }

    private fun invalidateSpans() {
        generation += 1
        val active = traces.values.toList()
        traces.clear()
        active.forEach { id -> bestEffort { backend.stopTrace(id, SpanStatus.OK) } }
    }

    private fun syncBackend() {
        if (!isDisabled && !backendStarted) {
            ensureStarted()
        } else if (isDisabled && backendStarted) {
            backendStarted = false
            bestEffort { backend.stop() }
        }
    }

    private fun ensureStarted(): Boolean {
        if (!backendStarted) {
            backendStarted = bestEffort { backend.start() }
            // A provider may have partly opted in before throwing. Roll that attempt back.
            if (!backendStarted) bestEffort { backend.stop() }
        }
        return backendStarted
    }

    private inline fun bestEffort(call: () -> Unit): Boolean = try {
        call()
        true
    } catch (_: Throwable) {
        false
    }

    public companion object {
        /** A new permanently build-disabled instance, not a mutable shared singleton. */
        public val disabled: Telemetry
            get() = Telemetry(DisabledTelemetryBackend, buildEnabled = false)
    }
}
