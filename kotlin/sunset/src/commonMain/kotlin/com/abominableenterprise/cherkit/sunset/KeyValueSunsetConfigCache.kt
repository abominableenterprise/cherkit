package com.abominableenterprise.cherkit.sunset

/**
 * Keeps the last good config body under [key] in [store], so Sunset still applies it when a launch is offline.
 * [SunsetChecker] saves only bodies that parsed; a malformed fetch never replaces it. Storage errors are ignored.
 */
public class KeyValueSunsetConfigCache(
    private val store: SunsetKeyValueStore,
    /** The key the body is stored under. */
    public val key: String = DEFAULT_KEY,
) : SunsetConfigCache {
    override suspend fun load(): String? = try {
        store.getString(key)
    } catch (_: Exception) {
        null
    }

    override suspend fun save(text: String) {
        try {
            store.putString(key, text)
        } catch (_: Exception) {
            // The cache is best effort: the next launch fetches again.
        }
    }

    public companion object {
        /** The default key. */
        public const val DEFAULT_KEY: String = "cherkit.sunset.config"
    }
}
