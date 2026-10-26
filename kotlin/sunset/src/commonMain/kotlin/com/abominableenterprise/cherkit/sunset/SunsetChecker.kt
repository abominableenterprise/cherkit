package com.abominableenterprise.cherkit.sunset

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The one call apps make at launch: fetch the hosted config, fall back to the last good one, and decide.
 *
 * It never throws (only the caller's own cancellation propagates) and fails open: an unreachable, slow, non-2xx or
 * malformed config uses the cached last good config, and with no usable config at all the answer is
 * [SunsetDecision.UpToDate]. Only a fetched body that parses is saved to the [cache].
 *
 * To turn the check off (UI tests, emulators), use [disabled]:
 * `val checker = if (isUiTest) SunsetChecker.disabled() else AndroidSunset.checker(context) { ... }`.
 */
public class SunsetChecker(
    /** Where the hosted config comes from, e.g. `FetchSunsetConfigSource { myHttpGet(url) }`. */
    public val source: SunsetConfigSource,
    /** Where the last good config is kept. On Android: `AndroidSunset.cache(context)`. */
    public val cache: SunsetConfigCache,
    /** The longest a launch waits for the fetch. */
    public val timeout: Duration = DEFAULT_TIMEOUT,
    /** Which block of the config applies to this app. */
    public val platform: SunsetPlatform = SunsetPlatform.ANDROID,
) {
    /**
     * Decides for this launch.
     *
     * @param installedVersion The installed app version, e.g. `versionName`.
     * @param osVersion The device OS version: the Android API level (`"34"`) or the iOS version.
     * @param locale The user's preferred language tags in order, e.g. `["de-DE", "en-US"]`.
     */
    public suspend fun check(installedVersion: String, osVersion: String, locale: List<String>): SunsetDecision {
        if (this === DISABLED) return SunsetDecision.UpToDate
        val fetched = fetchWithDeadline()
        val choice = SunsetConfigChoice.choose(fetched, failOpen { cache.load() })
        if (choice.source == SunsetConfigChoice.Source.FETCHED && fetched != null) failOpen { cache.save(fetched) }
        return SunsetDecider.decide(choice.config, platform, installedVersion, osVersion, locale)
    }

    /**
     * The source's body, or `null` after [timeout]. The fetch runs detached, so a source that blocks or ignores
     * cancellation is abandoned at the deadline instead of holding up the launch.
     */
    private suspend fun fetchWithDeadline(): String? {
        val fetch = CoroutineScope(Dispatchers.Default).async { source.fetch(timeout) }
        return try {
            failOpen { withTimeoutOrNull(timeout) { fetch.await() } }
        } finally {
            fetch.cancel()
        }
    }

    /** [block]'s result, or `null` when it throws. Rethrows only when the caller itself was cancelled. */
    private suspend fun <T> failOpen(block: suspend () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
        null
    } catch (_: Exception) {
        null
    }

    public companion object {
        /** The default fetch timeout. */
        public val DEFAULT_TIMEOUT: Duration = 5.seconds

        /**
         * A checker that is switched off: [check] always answers [SunsetDecision.UpToDate] without fetching anything
         * or reading or writing any cache. Use it wherever the app should never be prompted, e.g. UI tests and
         * emulators.
         */
        public fun disabled(): SunsetChecker = DISABLED

        private val DISABLED = SunsetChecker(DisabledSunsetConfigSource, DisabledSunsetConfigCache)
    }
}
