package com.abominableenterprise.cherkit.sunset

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * A [SunsetConfigSource] over the app's [SunsetFetch], wrapped in `withTimeoutOrNull`. A `null`, empty or thrown
 * result, or one slower than the timeout, is a failure (`null`).
 */
public class FetchSunsetConfigSource(private val fetch: SunsetFetch) : SunsetConfigSource {
    override suspend fun fetch(timeout: Duration): String? = try {
        withTimeoutOrNull(timeout) { fetch.fetch() }?.takeIf { it.isNotEmpty() }
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
        null
    } catch (_: Exception) {
        null
    }
}
