package com.abominableenterprise.cherkit.whatsnew

import android.content.Context
import android.content.res.AssetManager
import android.os.LocaleList
import java.io.InputStream

/** Android wiring for [WhatsNewChecker]: notes from `assets/whatsnew/`, state in SharedPreferences. */
public object AndroidWhatsNew {
    /** Default assets folder holding the notes files. */
    public const val DEFAULT_ASSETS_DIRECTORY: String = "whatsnew"

    /** Default SharedPreferences file name. */
    public const val DEFAULT_PREFERENCES_NAME: String = "cherkit.whatsnew"

    /** A checker over [assetsSource] and [store] for [context]. */
    public fun checker(context: Context, onSkip: SkipHandler? = null): WhatsNewChecker =
        WhatsNewChecker(assetsSource(context.assets, onSkip = onSkip), store(context))

    /** Every `.json` file in `assets/<directory>/`; invalid files are skipped and reported to [onSkip]. */
    public fun assetsSource(
        assets: AssetManager,
        directory: String = DEFAULT_ASSETS_DIRECTORY,
        onSkip: SkipHandler? = null,
    ): ReleaseNotesSource = LoadedReleaseNotesSource(onSkip) { readAssets(directory, assets::list, assets::open) }

    /** A [WhatsNewStore] in the SharedPreferences file [name], keys starting with [keyPrefix]. */
    public fun store(
        context: Context,
        name: String = DEFAULT_PREFERENCES_NAME,
        keyPrefix: String = KeyValueWhatsNewStore.DEFAULT_KEY_PREFIX,
    ): WhatsNewStore = KeyValueWhatsNewStore(
        SharedPreferencesKeyValueStorage(context.getSharedPreferences(name, Context.MODE_PRIVATE)),
        keyPrefix,
    )

    /** The app's `versionName` from the PackageManager, or `""` (which fails open) when unavailable. */
    @Suppress("DEPRECATION")
    public fun installedVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (_: Exception) {
        ""
    }

    /** The user's locales, most preferred first (e.g. `["de-DE", "en-US"]`), for `locale`. */
    public fun preferredLocales(): List<String> = LocaleList.getDefault().toLanguageTags().split(',').filter { it.isNotEmpty() }

    /**
     * The `.json` files in [directory] as (file name, text) pairs. A file that cannot be opened gets empty text, so it
     * is skipped and reported like any invalid file.
     */
    internal fun readAssets(
        directory: String,
        list: (String) -> Array<String>?,
        open: (String) -> InputStream,
    ): List<Pair<String, String>> = list(directory).orEmpty().filter { it.endsWith(".json") }.map { name ->
        val text = try {
            open("$directory/$name").use { it.readBytes().decodeToString() }
        } catch (_: Exception) {
            ""
        }
        name to text
    }
}
