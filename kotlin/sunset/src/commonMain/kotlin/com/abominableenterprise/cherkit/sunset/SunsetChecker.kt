package com.abominableenterprise.cherkit.sunset

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

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
    /**
     * The App Store lookup, or `null` (the default) when only the config can offer an update. Asked, with the same
     * [timeout] and alongside the config fetch, only on the [SunsetChannel.STORE] channel. Not cached: a failed lookup
     * just means no lookup prompt.
     */
    public val lookup: SunsetLookupSource? = null,
) {
    /** The 0.1.0 constructor, kept so code compiled against it still links. Source calls resolve to the one above. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    public constructor(
        source: SunsetConfigSource,
        cache: SunsetConfigCache,
        timeout: Duration = DEFAULT_TIMEOUT,
        platform: SunsetPlatform = SunsetPlatform.ANDROID,
    ) : this(source, cache, timeout, platform, null)

    /**
     * `true` only for [disabled]: [check] answers [SunsetDecision.UpToDate] at once. Read-only, so app tests can
     * assert they wired the switched-off checker, e.g. `assertTrue(testEnvironment.sunsetChecker.isDisabled)`.
     */
    public val isDisabled: Boolean get() = this === DISABLED

    /**
     * Decides for this launch.
     *
     * @param installedVersion The installed app version, e.g. `versionName`.
     * @param osVersion The device OS version: the Android API level (`"34"`) or the iOS version.
     * @param locale The user's preferred language tags in order, e.g. `["de-DE", "en-US"]`.
     * @param channel How this build was installed (default [SunsetChannel.STORE], the behaviour before channels).
     * @param installedBuild The installed build number (iOS `CFBundleVersion`), for TestFlight test-build prompts.
     */
    public suspend fun check(
        installedVersion: String,
        osVersion: String,
        locale: List<String>,
        channel: SunsetChannel = SunsetChannel.STORE,
        installedBuild: String? = null,
    ): SunsetDecision = checkReportingFailure(installedVersion, osVersion, locale, channel, installedBuild).decision

    /** The 0.1.0 signature, kept so code compiled against it still links. Source calls resolve to the one above. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    public suspend fun check(installedVersion: String, osVersion: String, locale: List<String>): SunsetDecision =
        check(installedVersion, osVersion, locale, SunsetChannel.STORE)

    /**
     * [check] plus whether it failed: [SunsetCheckOutcome.failed] is `true` when there was no usable config at all
     * (the fetch failed, timed out or did not parse, and nothing usable was cached), so the fail-open
     * [SunsetDecision.UpToDate] is not a real answer. sunset-compose's `SunsetPrompt(checker = ...)` uses it to retry;
     * most apps call [check]. Like [check] it never throws, and [disabled] never fails.
     *
     * @param installedVersion The installed app version, e.g. `versionName`.
     * @param osVersion The device OS version: the Android API level (`"34"`) or the iOS version.
     * @param locale The user's preferred language tags in order, e.g. `["de-DE", "en-US"]`.
     * @param channel How this build was installed (default [SunsetChannel.STORE], the behaviour before channels).
     * @param installedBuild The installed build number (iOS `CFBundleVersion`), for TestFlight test-build prompts.
     * @param now The device clock, compared with the TestFlight block's `notBefore`.
     */
    public suspend fun checkReportingFailure(
        installedVersion: String,
        osVersion: String,
        locale: List<String>,
        channel: SunsetChannel = SunsetChannel.STORE,
        installedBuild: String? = null,
        now: Instant = Clock.System.now(),
    ): SunsetCheckOutcome {
        if (isDisabled) return SunsetCheckOutcome(SunsetDecision.UpToDate, failed = false)
        val lookupSource = lookup?.takeIf { channel == SunsetChannel.STORE }
        val looked = lookupSource?.let { CoroutineScope(Dispatchers.Default).async { withDeadline { it.fetch(timeout) } } }
        val fetched: String?
        val lookupBody: String?
        try {
            fetched = withDeadline { source.fetch(timeout) }
            lookupBody = looked?.let { failOpen { it.await() } }
        } finally {
            looked?.cancel()
        }
        val choice = SunsetConfigChoice.choose(fetched, failOpen { cache.load() })
        if (choice.source == SunsetConfigChoice.Source.FETCHED && fetched != null) failOpen { cache.save(fetched) }
        val lookupResult = lookupBody?.let(SunsetLookupResult::parse)
        val decision = SunsetDecider.decide(
            choice.config, platform, installedVersion, osVersion, locale, channel, installedBuild, lookupResult, now,
        )
        return SunsetCheckOutcome(decision, failed = choice.source == SunsetConfigChoice.Source.MISSING)
    }

    /** The 0.1.0 signature, kept so code compiled against it still links. Source calls resolve to the one above. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    public suspend fun checkReportingFailure(
        installedVersion: String,
        osVersion: String,
        locale: List<String>,
    ): SunsetCheckOutcome = checkReportingFailure(installedVersion, osVersion, locale, SunsetChannel.STORE)

    /**
     * [fetch]'s body, or `null` after [timeout]. The fetch runs detached, so a source that blocks or ignores
     * cancellation is abandoned at the deadline instead of holding up the launch.
     */
    private suspend fun withDeadline(fetch: suspend () -> String?): String? {
        val job = CoroutineScope(Dispatchers.Default).async { fetch() }
        return try {
            failOpen { withTimeoutOrNull(timeout) { job.await() } }
        } finally {
            job.cancel()
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
