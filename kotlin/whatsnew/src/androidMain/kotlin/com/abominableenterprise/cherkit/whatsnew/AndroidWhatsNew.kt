package com.abominableenterprise.cherkit.whatsnew

import android.content.Context
import android.content.res.AssetManager
import android.os.LocaleList
import com.abominableenterprise.cherkit.core.TelemetrySink
import java.io.InputStream

/** Android wiring for [WhatsNewChecker]: notes from `assets/whatsnew/`, state in SharedPreferences. */
public object AndroidWhatsNew {
    /** Default assets folder holding the notes files. */
    public const val DEFAULT_ASSETS_DIRECTORY: String = "whatsnew"

    /** Default SharedPreferences file name. */
    public const val DEFAULT_PREFERENCES_NAME: String = "cherkit.whatsnew"

    /** A checker over [assetsSource] and [store] for [context]. */
    public fun checker(
        context: Context,
        telemetrySink: TelemetrySink? = null,
        onSkip: SkipHandler? = null,
    ): WhatsNewChecker = WhatsNewChecker(assetsSource(context.assets, onSkip = onSkip), store(context), telemetrySink)

    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    public fun checker(context: Context, onSkip: SkipHandler? = null): WhatsNewChecker = checker(context, null, onSkip)

    /** Source compatibility for callers passing the skip callback positionally. */
    @Suppress("UNUSED_PARAMETER")
    public fun checker(
        context: Context,
        legacyOnSkip: SkipHandler?,
        compatibility: Unit = Unit,
    ): WhatsNewChecker = checker(context, null, legacyOnSkip)

    /**
     * Every bundled release newest first, the same list as `checker(context).history(locales)`, for a Settings screen
     * that has no checker of its own. Read-only: it never appends to the install history or marks anything seen.
     * Invalid notes files are skipped; anything unreadable fails open (an empty list). It reads the assets, so call it
     * off the main thread or remember the result.
     *
     * @param locales The user's locales, most preferred first; [preferredLocales] by default.
     */
    public fun history(
        context: Context,
        locales: List<String> = preferredLocales(),
        telemetrySink: TelemetrySink? = null,
    ): List<WhatsNewHistoryEntry> = WhatsNewChecker(assetsSource(context.assets), store(context), telemetrySink).history(locales)

    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    public fun history(context: Context, locales: List<String> = preferredLocales()): List<WhatsNewHistoryEntry> =
        history(context, locales, null)

    /** [history] over any [source] and [store], so host tests can pass fakes. */
    internal fun history(source: ReleaseNotesSource, store: WhatsNewStore, locales: List<String>): List<WhatsNewHistoryEntry> =
        WhatsNewChecker(source, store).history(locales)

    /** Every `.json` file in `assets/<directory>/`; invalid files are skipped and reported to [onSkip]. */
    public fun assetsSource(
        assets: AssetManager,
        directory: String = DEFAULT_ASSETS_DIRECTORY,
        telemetrySink: TelemetrySink? = null,
        onSkip: SkipHandler? = null,
    ): ReleaseNotesSource = LoadedReleaseNotesSource.fromFiles(onSkip, telemetrySink) {
        readAssetFiles(directory, assets::list, assets::open)
    }

    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    public fun assetsSource(
        assets: AssetManager,
        directory: String = DEFAULT_ASSETS_DIRECTORY,
        onSkip: SkipHandler? = null,
    ): ReleaseNotesSource = assetsSource(assets, directory, null, onSkip)

    /** Source compatibility for callers passing the skip callback positionally. */
    @Suppress("UNUSED_PARAMETER")
    public fun assetsSource(
        assets: AssetManager,
        directory: String = DEFAULT_ASSETS_DIRECTORY,
        legacyOnSkip: SkipHandler?,
        compatibility: Unit = Unit,
    ): ReleaseNotesSource = assetsSource(assets, directory, null, legacyOnSkip)

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
    ): List<Pair<String, String>> = readAssetFiles(directory, list, open).files.map { (name, bytes) ->
        name to bytes.decodeToString()
    }

    /** Keep missing files distinct from malformed bytes; never report both codes for one open failure. */
    internal fun readAssetFiles(
        directory: String,
        list: (String) -> Array<String>?,
        open: (String) -> InputStream,
    ): NotesFiles {
        val missing = mutableSetOf<String>()
        val files = list(directory).orEmpty().filter { it.endsWith(".json") }.associateWith { name ->
            try {
                open("$directory/$name").use { it.readBytes() }
            } catch (_: Exception) {
                missing += name
                byteArrayOf()
            }
        }
        return NotesFiles(files, missing)
    }
}
