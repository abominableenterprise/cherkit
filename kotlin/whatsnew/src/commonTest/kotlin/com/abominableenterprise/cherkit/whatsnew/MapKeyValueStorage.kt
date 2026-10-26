package com.abominableenterprise.cherkit.whatsnew

/** In-memory [KeyValueStorage]. */
internal class MapKeyValueStorage(val values: MutableMap<String, String> = mutableMapOf()) : KeyValueStorage {
    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }
}
