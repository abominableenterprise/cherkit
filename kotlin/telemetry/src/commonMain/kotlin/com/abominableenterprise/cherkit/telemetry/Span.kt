package com.abominableenterprise.cherkit.telemetry

/** A manually managed trace. Gate changes permanently invalidate it; ending is idempotent. */
public class Span internal constructor(
    private val attribute: (String, Any) -> Unit = { _, _ -> },
    private val finish: (SpanStatus) -> Unit = {},
) {
    private var ended = false

    public fun setAttribute(key: String, value: Any) {
        if (!ended) attribute(key, value)
    }

    public fun end(status: SpanStatus = SpanStatus.OK) {
        if (ended) return
        ended = true
        finish(status)
    }
}
