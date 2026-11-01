package com.abominableenterprise.cherkit.sunset

import com.abominableenterprise.cherkit.core.AppVersion
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * The parts of Apple's public App Store lookup that Sunset uses: the store's current version, the OS it needs, and its
 * store page.
 */
public data class SunsetLookupResult(
    /** `results[0].version`: the version live in the App Store. */
    public val version: AppVersion,
    /** `results[0].minimumOsVersion`: devices below it cannot install [version] and get no lookup prompt. */
    public val minimumOsVersion: AppVersion,
    /** `results[0].trackViewUrl` when it is a plain `https` link (config `storeURL` rules), else `null`. */
    public val trackViewUrl: String? = null,
) {
    public companion object {
        /**
         * Parses a lookup response, or returns `null` (no lookup prompt: fail open) when it is not JSON, `resultCount`
         * is not a number above 0, `results[0]` is not an object, or its `version` or `minimumOsVersion` is missing or
         * not a valid `AppVersion` string. A missing or non-`https` `trackViewUrl` only drops [trackViewUrl].
         * The contract is `fixtures/sunset/lookup.json`, shared with Swift's `SunsetLookupResult.parse`.
         */
        public fun parse(text: String): SunsetLookupResult? {
            val root = try {
                Json.parseToJsonElement(text)
            } catch (_: SerializationException) {
                return null
            } catch (_: IllegalArgumentException) {
                return null
            }
            if (root !is JsonObject) return null
            val count = (root["resultCount"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: return null
            if (count < 1) return null
            val first = (root["results"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
            val version = string(first["version"])?.let(AppVersion::parse) ?: return null
            val minimumOs = string(first["minimumOsVersion"])?.let(AppVersion::parse) ?: return null
            val url = string(first["trackViewUrl"])?.takeIf(SunsetStoreUrl::isValid)
            return SunsetLookupResult(version, minimumOs, url)
        }

        private fun string(value: Any?): String? = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
    }
}
