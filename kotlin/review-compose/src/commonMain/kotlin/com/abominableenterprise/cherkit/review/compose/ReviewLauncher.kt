package com.abominableenterprise.cherkit.review.compose

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
     */
    public suspend fun <T : Any> launch(
        coordinator: ReviewCoordinator,
        flow: ReviewFlow<T>,
        isIdle: () -> Boolean = { true },
    ): ReviewLaunchResult {
        if (!coordinator.shouldPrompt(isIdle())) return ReviewLaunchResult.SKIPPED
        val info = try {
            flow.request()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return ReviewLaunchResult.REQUEST_FAILED
        }
        if (!isIdle() || !coordinator.markPrompted()) return ReviewLaunchResult.SKIPPED
        return try {
            flow.launch(info)
            ReviewLaunchResult.LAUNCHED
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ReviewLaunchResult.LAUNCH_FAILED
        }
    }
}
