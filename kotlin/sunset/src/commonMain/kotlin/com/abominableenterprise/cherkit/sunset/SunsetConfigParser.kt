package com.abominableenterprise.cherkit.sunset

import com.abominableenterprise.cherkit.core.AppVersion
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** Turns hosted JSON into a [SunsetConfig], dropping whatever is malformed (see [SunsetConfig.parse]). */
internal object SunsetConfigParser {
    fun parse(text: String): SunsetConfig? {
        val root = try {
            Json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (root !is JsonObject) return null
        val schema = root["schemaVersion"] as? JsonPrimitive ?: return null
        if (schema.isString || schema is JsonNull) return null
        if (schema.doubleOrNull != SunsetConfig.SUPPORTED_SCHEMA_VERSION.toDouble()) return null
        return SunsetConfig(
            ios = block(root[SunsetPlatform.IOS.key]),
            android = block(root[SunsetPlatform.ANDROID.key]),
        )
    }

    private fun block(value: JsonElement?): SunsetPlatformConfig? {
        if (value !is JsonObject) return null
        val minimum = version(value["minimumVersion"]) ?: return null
        val latest = version(value["latestVersion"]) ?: return null
        val minimumOS = version(value["latestMinimumOS"]) ?: return null
        val url = value["storeURL"] as? JsonPrimitive ?: return null
        if (!url.isString || !SunsetStoreUrl.isValid(url.content)) return null
        return SunsetPlatformConfig(
            minimumVersion = minimum.value,
            latestVersion = latest.value,
            latestMinimumOS = minimumOS.value,
            storeUrl = url.content,
            messages = messages(value["message"]),
        )
    }

    /** A present-or-absent version; `null` from [version] itself means invalid. */
    private class OptionalVersion(val value: AppVersion?)

    /** Missing or `null` is a valid absent version; anything but a parseable string is invalid. */
    private fun version(value: JsonElement?): OptionalVersion? = when {
        value == null || value is JsonNull -> OptionalVersion(null)
        value is JsonPrimitive && value.isString -> AppVersion.parse(value.content)?.let { OptionalVersion(it) }
        else -> null
    }

    /** String entries of the `message` object; anything else is dropped. */
    private fun messages(value: JsonElement?): Map<String, String> {
        if (value !is JsonObject) return emptyMap()
        return value.mapNotNull { (key, entry) ->
            (entry as? JsonPrimitive)?.takeIf { it.isString }?.let { key to it.content }
        }.toMap()
    }
}
