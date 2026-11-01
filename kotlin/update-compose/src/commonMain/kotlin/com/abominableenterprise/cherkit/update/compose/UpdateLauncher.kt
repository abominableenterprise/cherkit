package com.abominableenterprise.cherkit.update.compose

import kotlin.coroutines.cancellation.CancellationException

/** The launch order over any [UpdateFlow]; plain Kotlin, so it runs on the JVM. `PlayUpdatePrompt` calls it. */
public object UpdateLauncher {
    /**
     * One attempt, in order:
     *
     * 1. `request()`; a failure gives [UpdateLaunchResult.CHECK_FAILED].
     * 2. A downloaded update gives [UpdateLaunchResult.DOWNLOADED] (also after this session's prompt).
     * 3. Already started this [session]: [UpdateLaunchResult.ALREADY_PROMPTED].
     * 4. No update or flexible not allowed: [UpdateLaunchResult.NO_UPDATE].
     * 5. "Not now" for this versionCode in [store]: [UpdateLaunchResult.DECLINED_BEFORE].
     * 6. [isAllowed] false (suppressed, or the screen left meanwhile): [UpdateLaunchResult.SUPPRESSED].
     * 7. `startFlexible()`: [UpdateLaunchResult.STARTED] and the session counts as prompted, else
     *    [UpdateLaunchResult.START_FAILED].
     *
     * Never throws (except cancellation).
     */
    public suspend fun <T> launch(
        flow: UpdateFlow<T>,
        store: UpdatePromptStore,
        session: UpdateSession,
        isAllowed: () -> Boolean = { true },
    ): UpdateLaunchResult {
        val info: T
        val status = try {
            info = flow.request()
            flow.status(info)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return UpdateLaunchResult.CHECK_FAILED
        }
        if (status.downloaded) return UpdateLaunchResult.DOWNLOADED
        if (session.prompted) return UpdateLaunchResult.ALREADY_PROMPTED
        if (!status.updateAvailable || !status.flexibleAllowed) return UpdateLaunchResult.NO_UPDATE
        if (declined(store) == status.availableVersionCode) return UpdateLaunchResult.DECLINED_BEFORE
        if (!isAllowed()) return UpdateLaunchResult.SUPPRESSED
        val started = try {
            flow.startFlexible(info)
        } catch (_: Exception) {
            false
        }
        if (!started) return UpdateLaunchResult.START_FAILED
        session.markPrompted(status.availableVersionCode)
        return UpdateLaunchResult.STARTED
    }

    /** Applies the sheet's answer for [versionCode]: [UpdateFlowResult.CANCELLED] is remembered in [store]. */
    public fun finish(result: UpdateFlowResult, versionCode: Int, store: UpdatePromptStore) {
        if (result != UpdateFlowResult.CANCELLED) return
        try {
            store.rememberDeclined(versionCode)
        } catch (_: Exception) {
            // A broken store only means the version may be offered again.
        }
    }

    private fun declined(store: UpdatePromptStore): Int? = try {
        store.declinedVersionCode
    } catch (_: Exception) {
        null
    }
}
