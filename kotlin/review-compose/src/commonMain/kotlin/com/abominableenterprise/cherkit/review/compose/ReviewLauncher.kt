package com.abominableenterprise.cherkit.review.compose

import com.abominableenterprise.cherkit.core.TelemetrySink
import com.abominableenterprise.cherkit.review.ReviewCoordinator
import kotlin.coroutines.cancellation.CancellationException

/** The launch order every adapter follows; platform-free so it is tested on the JVM. */
public object ReviewLauncher {
    /**
     * Asks [coordinator], then runs [flow]:
     *
     * 1. `shouldPrompt(isIdle())`, else [ReviewLaunchResult.SKIPPED].
     * 2. `flow.request()`. If it fails, [ReviewLaunchResult.REQUEST_FAILED]: the version is **not** consumed and the
     *    prompt stays due for the next check.
     * 3. `markPrompted()` (it rechecks `shouldPrompt`, and [isIdle] is read again: the screen may have changed during
     *    the request), else [ReviewLaunchResult.SKIPPED].
     * 4. `flow.launch(info)`: [ReviewLaunchResult.LAUNCHED], or [ReviewLaunchResult.LAUNCH_FAILED] if it throws.
     *
     * Never throws except for cancellation; a cancelled request consumes nothing. Call it from the main thread.
     * [telemetrySink] reports request failure as `cherkit.review`/1 and launch failure as /2, without attributes.
     * Only the coordinator emits `cherkit.review.prompted`; this layer never duplicates that breadcrumb.
     */
    public suspend fun <T : Any> launch(
        coordinator: ReviewCoordinator,
        flow: ReviewFlow<T>,
        telemetrySink: TelemetrySink? = null,
        isIdle: () -> Boolean = { true },
    ): ReviewLaunchResult {
        if (!coordinator.shouldPrompt(isIdle())) return ReviewLaunchResult.SKIPPED
        val info = try {
            flow.request()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ReviewTelemetry.report(telemetrySink, 1)
            return ReviewLaunchResult.REQUEST_FAILED
        }
        if (!isIdle() || !coordinator.markPrompted()) return ReviewLaunchResult.SKIPPED
        return try {
            flow.launch(info)
            ReviewLaunchResult.LAUNCHED
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ReviewTelemetry.report(telemetrySink, 2)
            ReviewLaunchResult.LAUNCH_FAILED
        }
    }

    /** Keeps positional idle callbacks source-compatible; named and trailing callbacks use the sink-aware API. */
    @Suppress("UNUSED_PARAMETER")
    public suspend fun <T : Any> launch(
        coordinator: ReviewCoordinator,
        flow: ReviewFlow<T>,
        legacyIsIdle: () -> Boolean,
        compatibility: Unit = Unit,
    ): ReviewLaunchResult = launch(coordinator, flow, telemetrySink = null, isIdle = legacyIsIdle)

    /** The original launch signature, retained for already compiled callers. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    public suspend fun <T : Any> launch(
        coordinator: ReviewCoordinator,
        flow: ReviewFlow<T>,
        isIdle: () -> Boolean = { true },
    ): ReviewLaunchResult = launch(coordinator, flow, telemetrySink = null, isIdle = isIdle)
}
