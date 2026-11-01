package com.abominableenterprise.cherkit.review

/**
 * The tiny typed key-value storage behind [KeyValueReviewStore]. On Android use `SharedPreferencesReviewStorage`.
 * Each getter returns `null` when the key is absent or holds another type, and must not throw.
 */
public interface ReviewKeyValueStorage {
    public fun getInt(key: String): Int?
    public fun getBoolean(key: String): Boolean?
    public fun getString(key: String): String?

    /** Writes all of [values] (`Int`, `Boolean`, `String`, or `null` to remove) together. */
    public fun put(values: Map<String, Any?>)
}
