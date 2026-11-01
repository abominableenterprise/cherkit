package com.abominableenterprise.cherkit.update.compose

/**
 * The store API [UpdateLauncher] drives, so tests (and other stores) can fake it. On Android the Play adapter
 * implements it over `AppUpdateManager`; [T] is the store's own info object (Play's `AppUpdateInfo`).
 */
public interface UpdateFlow<T> {
    /** Asks the store about updates (Play: `appUpdateInfo`). May throw; the launcher fails quietly. */
    public suspend fun request(): T

    /** [info] in plain terms. */
    public fun status(info: T): UpdateStatus

    /** Starts the flexible flow for [info]; `true` if the store's sheet is now showing. May throw. */
    public fun startFlexible(info: T): Boolean
}
