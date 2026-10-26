package com.abominableenterprise.cherkit.whatsnew

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * [WhatsNewStore] in a [KeyValueStorage]: the last-seen version as a string under `<keyPrefix>lastSeenVersion` and the
 * install history as a JSON array of strings under `<keyPrefix>installHistory`. A corrupt history counts as empty, so
 * the module fails open.
 */
public class KeyValueWhatsNewStore(
    private val storage: KeyValueStorage,
    private val keyPrefix: String = DEFAULT_KEY_PREFIX,
) : WhatsNewStore {
    private val lastSeenKey get() = keyPrefix + "lastSeenVersion"
    private val historyKey get() = keyPrefix + "installHistory"

    override fun lastSeenVersion(): String? = storage.getString(lastSeenKey)

    override fun setLastSeenVersion(version: String): Unit = storage.putString(lastSeenKey, version)

    override fun installHistory(): List<String> {
        val text = storage.getString(historyKey) ?: return emptyList()
        return try {
            (Json.parseToJsonElement(text) as JsonArray).map { item ->
                item.jsonPrimitive.takeIf { it.isString }?.contentOrNull ?: return emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun setInstallHistory(history: List<String>): Unit =
        storage.putString(historyKey, JsonArray(history.map(::JsonPrimitive)).toString())

    public companion object {
        /** Default prefix of the keys. */
        public const val DEFAULT_KEY_PREFIX: String = "cherkit.whatsnew."
    }
}
