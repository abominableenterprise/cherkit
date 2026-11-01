package com.abominableenterprise.cherkit.review.compose

/** A [ReviewFlow] that records its calls and fails on demand. */
class FakeReviewFlow(
    var requestFails: Boolean = false,
    var launchFails: Boolean = false,
    val onRequest: () -> Unit = {},
) : ReviewFlow<String> {
    val calls = mutableListOf<String>()

    override suspend fun request(): String {
        calls += "request"
        onRequest()
        if (requestFails) throw IllegalStateException("Play is unavailable")
        return "info"
    }

    override suspend fun launch(info: String) {
        calls += "launch:$info"
        if (launchFails) throw IllegalStateException("launch failed")
    }
}
