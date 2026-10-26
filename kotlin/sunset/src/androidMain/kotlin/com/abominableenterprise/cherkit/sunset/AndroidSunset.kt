package com.abominableenterprise.cherkit.sunset

import android.content.Context
import android.os.Build
import android.os.LocaleList
import kotlin.time.Duration

/** Android wiring for [SunsetChecker]: the cache in SharedPreferences and the app and OS versions. */
public object AndroidSunset {
    /** Default SharedPreferences file name. */
    public const val DEFAULT_PREFERENCES_NAME: String = "cherkit.sunset"

    /** A checker that fetches with [fetch] and caches in [cache] of [context]. */
    public fun checker(
        context: Context,
        timeout: Duration = SunsetChecker.DEFAULT_TIMEOUT,
        fetch: SunsetFetch,
    ): SunsetChecker = SunsetChecker(FetchSunsetConfigSource(fetch), cache(context), timeout)

    /** A cache in the SharedPreferences file [name] under [key]. */
    public fun cache(
        context: Context,
        name: String = DEFAULT_PREFERENCES_NAME,
        key: String = KeyValueSunsetConfigCache.DEFAULT_KEY,
    ): SunsetConfigCache = KeyValueSunsetConfigCache(
        SharedPreferencesSunsetStore(context.getSharedPreferences(name, Context.MODE_PRIVATE)),
        key,
    )

    /** The app's `versionName` from the PackageManager, or `""` (which fails open) when unavailable. */
    @Suppress("DEPRECATION")
    public fun installedVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (_: Exception) {
        ""
    }

    /** The device API level (`Build.VERSION.SDK_INT`), e.g. `"34"`, compared against `latestMinimumOS`. */
    public fun osVersion(): String = Build.VERSION.SDK_INT.toString()

    /** The user's locales, most preferred first (e.g. `["de-DE", "en-US"]`). */
    public fun preferredLocales(): List<String> =
        LocaleList.getDefault().toLanguageTags().split(',').filter { it.isNotEmpty() }
}

/** [SunsetChecker.check] for the running app: its `versionName`, `Build.VERSION.SDK_INT` and the user's locales. */
public suspend fun SunsetChecker.check(
    context: Context,
    locale: List<String> = AndroidSunset.preferredLocales(),
): SunsetDecision = check(AndroidSunset.installedVersion(context), AndroidSunset.osVersion(), locale)
