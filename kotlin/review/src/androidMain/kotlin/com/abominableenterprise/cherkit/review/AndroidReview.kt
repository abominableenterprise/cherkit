package com.abominableenterprise.cherkit.review

import android.content.Context
import android.content.pm.ApplicationInfo
import com.abominableenterprise.cherkit.core.TelemetrySink

/** Android wiring for [ReviewCoordinator]: state in SharedPreferences, version from the PackageManager. */
public object AndroidReview {
    /** Default SharedPreferences file name. */
    public const val DEFAULT_PREFERENCES_NAME: String = "cherkit.review"

    /**
     * A coordinator over [store] for the installed `versionName`. [enabled] defaults to "not a debuggable build", so
     * debug builds never prompt; pass `false` in instrumented tests too.
     */
    public fun coordinator(
        context: Context,
        enabled: Boolean = !isDebuggable(context),
        telemetrySink: TelemetrySink? = null,
        isEligible: () -> Boolean = { true },
    ): ReviewCoordinator = ReviewCoordinator(
        store = store(context),
        appVersion = installedVersion(context),
        enabled = enabled,
        isEligible = isEligible,
        telemetrySink = telemetrySink,
    )

    /** Keeps positional eligibility callbacks source-compatible without changing trailing-lambda calls. */
    @Suppress("UNUSED_PARAMETER")
    public fun coordinator(
        context: Context,
        enabled: Boolean = !isDebuggable(context),
        legacyIsEligible: () -> Boolean,
        compatibility: Unit = Unit,
    ): ReviewCoordinator = coordinator(context, enabled, telemetrySink = null, isEligible = legacyIsEligible)

    /** The original factory signature, retained for already compiled callers. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    public fun coordinator(
        context: Context,
        enabled: Boolean = !isDebuggable(context),
        isEligible: () -> Boolean = { true },
    ): ReviewCoordinator = coordinator(context, enabled, telemetrySink = null, isEligible = isEligible)

    /** A [ReviewStore] in the SharedPreferences file [name], keys starting with [keyPrefix]. */
    public fun store(
        context: Context,
        name: String = DEFAULT_PREFERENCES_NAME,
        keyPrefix: String = KeyValueReviewStore.DEFAULT_KEY_PREFIX,
    ): ReviewStore = KeyValueReviewStore(
        SharedPreferencesReviewStorage(context.getSharedPreferences(name, Context.MODE_PRIVATE)),
        keyPrefix,
    )

    /** The app's `versionName`, or `""` when unavailable. */
    @Suppress("DEPRECATION")
    public fun installedVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (_: Exception) {
        ""
    }

    private fun isDebuggable(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
