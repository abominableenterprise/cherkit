package com.abominableenterprise.cherkit.sunset

/**
 * The app's HTTP GET of the hosted config, so CherKit needs no HTTP client. Return the body of a 2xx response, or
 * `null` for anything else (use [bodyIfSuccess]); throwing counts as a failure too. Turn off HTTP caching for this
 * request if your client caches, so a raised `minimumVersion` arrives on the next launch.
 */
public fun interface SunsetFetch {
    /** The response body of a 2xx GET of the hosted config, or `null`. */
    public suspend fun fetch(): String?

    public companion object {
        /** [body] when [statusCode] is 2xx and [body] is not empty, else `null`. */
        public fun bodyIfSuccess(statusCode: Int, body: String?): String? =
            body?.takeIf { statusCode in 200..299 && it.isNotEmpty() }
    }
}
