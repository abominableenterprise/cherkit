package com.abominableenterprise.cherkit.review

import com.abominableenterprise.cherkit.core.TelemetrySink
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * The store-review ("rate this app") rules. Pure logic: it never shows anything; a platform adapter asks
 * [shouldPrompt] and calls [markPrompted] right before it hands off to the platform review flow.
 *
 * - **Due** after the [successThreshold]th [recordSuccess] (counted across launches and versions until the next
 *   prompt) or an [arm]. The due flag persists until a prompt is shown.
 * - **At most once per app version**: after a prompt the count restarts at 0 and triggers in that same version are
 *   ignored. A later version prompts again once re-armed (by [arm] or N fresh successes).
 * - **Eligibility**: while [isEligible] is false nothing counts or arms and nothing prompts; a due prompt is held.
 * - **Interruptions**: [noteInterruption] or a source hidden through [setInterruptionVisible] holds the prompt for
 *   [cooldown] (it stays due); while any source is visible it never prompts.
 *
 * Not thread-safe: call it from the main thread.
 *
 * @param store Where the state persists; on Android `AndroidReview.store(context)`.
 * @param appVersion The installed version (`versionName`); one prompt per distinct string.
 * @param enabled Pass `false` for debug builds and test hosts; then the coordinator [isDisabled].
 * @param successThreshold The [recordSuccess] call that makes the prompt due; values below 1 count as 1.
 * @param cooldown How long an interruption holds the prompt; negative values count as zero.
 * @param isEligible Whether the current session may count and prompt, read on every call.
 * @param clock Epoch milliseconds.
 * @param telemetrySink Optional diagnostics; only a fixed breadcrumb name, never versions or interruption sources.
 */
public class ReviewCoordinator(
    private val store: ReviewStore,
    public val appVersion: String,
    enabled: Boolean = true,
    successThreshold: Int = DEFAULT_SUCCESS_THRESHOLD,
    cooldown: Duration = DEFAULT_COOLDOWN,
    public var isEligible: () -> Boolean = { true },
    private val telemetrySink: TelemetrySink? = null,
    private val clock: () -> Long = ::currentTimeMillis,
) {
    /** Keeps positional callbacks source-compatible; named and trailing callbacks use the primary constructor. */
    @Suppress("UNUSED_PARAMETER")
    public constructor(
        store: ReviewStore,
        appVersion: String,
        enabled: Boolean = true,
        successThreshold: Int = DEFAULT_SUCCESS_THRESHOLD,
        cooldown: Duration = DEFAULT_COOLDOWN,
        legacyIsEligible: () -> Boolean,
        legacyClock: () -> Long,
        compatibility: Unit = Unit,
    ) : this(store, appVersion, enabled, successThreshold, cooldown, legacyIsEligible, null, legacyClock)

    /** The original constructor, including its defaults, retained for already compiled callers. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    public constructor(
        store: ReviewStore,
        appVersion: String,
        enabled: Boolean = true,
        successThreshold: Int = DEFAULT_SUCCESS_THRESHOLD,
        cooldown: Duration = DEFAULT_COOLDOWN,
        isEligible: () -> Boolean = { true },
        clock: () -> Long = ::currentTimeMillis,
    ) : this(store, appVersion, enabled, successThreshold, cooldown, isEligible, null, clock)

    /** `true` for [disabled] and `enabled = false`: nothing counts, arms or prompts and the store is never touched. */
    public val isDisabled: Boolean = !enabled

    /** Successes that make the prompt due (at least 1). */
    public val successThreshold: Int = successThreshold.coerceAtLeast(1)

    /** How long an interruption holds the prompt (at least zero). */
    public val cooldown: Duration = cooldown.coerceAtLeast(Duration.ZERO)

    private var state: ReviewState = if (enabled) store.load() else ReviewState()
    private var lastInterruptionMillis: Long? = null
    private val visible = mutableSetOf<String>()

    /** The interruption sources currently visible. */
    public val visibleInterruptions: Set<String> get() = visible.toSet()

    /** Something the app counts as a success happened (e.g. an entry was saved). The Nth makes the prompt due. */
    public fun recordSuccess() {
        if (!acceptsTriggers) return
        val count = state.successCount.coerceAtLeast(0).let { if (it == Int.MAX_VALUE) it else it + 1 }
        update(state.copy(successCount = count, isDue = state.isDue || count >= successThreshold))
    }

    /** Makes the prompt due now, e.g. for a milestone. */
    public fun arm() {
        if (!acceptsTriggers) return
        update(state.copy(isDue = true))
    }

    /** A paywall, error or failed write was shown: hold the prompt for [cooldown] from now. It stays due. */
    public fun noteInterruption() {
        if (isDisabled) return
        lastInterruptionMillis = clock()
    }

    /**
     * Reports one of the app's own modals. While any [source] is visible the prompt never shows; hiding a visible
     * source starts the [cooldown], so the prompt waits for the next settle after it instead of firing at once.
     */
    public fun setInterruptionVisible(visible: Boolean, source: String) {
        if (isDisabled) return
        if (visible) {
            this.visible += source
        } else if (this.visible.remove(source)) {
            lastInterruptionMillis = clock()
        }
    }

    /** A trigger fired and this version has not prompted yet. Ignores eligibility and interruptions. */
    public val isDue: Boolean get() = !isDisabled && state.isDue && !promptedThisVersion

    /** How long the last interruption still holds the prompt; zero when it no longer does. */
    public val cooldownRemaining: Duration
        get() {
            val last = lastInterruptionMillis ?: return Duration.ZERO
            val left = (last + cooldown.inWholeMilliseconds - clock()).milliseconds
            return left.coerceIn(Duration.ZERO, cooldown)
        }

    /**
     * Whether to ask now: due, eligible, [isIdle] (the adapter's settled screen), no interruption visible and the
     * cooldown over.
     */
    public fun shouldPrompt(isIdle: Boolean = true): Boolean =
        isIdle && isDue && isEligible() && visible.isEmpty() && cooldownRemaining == Duration.ZERO

    /**
     * Records the prompt for [appVersion], right before the adapter launches the platform review flow. Rechecks
     * [shouldPrompt] and returns `false` (changing nothing) when it no longer holds; then don't launch.
     */
    public fun markPrompted(): Boolean {
        if (!shouldPrompt()) return false
        update(ReviewState(successCount = 0, isDue = false, lastPromptedVersion = appVersion))
        try {
            telemetrySink?.breadcrumb("cherkit.review.prompted")
        } catch (_: Exception) {
            // Optional diagnostics must not turn a successfully persisted prompt into a failed handoff.
        }
        return true
    }

    private val promptedThisVersion: Boolean get() = state.lastPromptedVersion == appVersion

    private val acceptsTriggers: Boolean get() = !isDisabled && isEligible() && !promptedThisVersion

    private fun update(newState: ReviewState) {
        state = newState
        store.save(newState)
    }

    public companion object {
        /** Successes that make the prompt due by default. */
        public const val DEFAULT_SUCCESS_THRESHOLD: Int = 5

        /** How long an interruption holds the prompt by default. */
        public val DEFAULT_COOLDOWN: Duration = 2.minutes

        /**
         * A coordinator that is switched off: it never counts, arms or prompts and never touches a store. Use it in
         * UI tests and previews: `if (isUiTest) ReviewCoordinator.disabled() else AndroidReview.coordinator(context)`.
         */
        public fun disabled(): ReviewCoordinator = ReviewCoordinator(InMemoryReviewStore(), appVersion = "", enabled = false)
    }
}
