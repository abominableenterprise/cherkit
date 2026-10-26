package com.abominableenterprise.cherkit.sunset

/** In-memory [SunsetConfigCache] that records every save. */
internal class MemorySunsetCache(private var stored: String? = null) : SunsetConfigCache {
    val saved = mutableListOf<String>()

    override suspend fun load(): String? = stored

    override suspend fun save(text: String) {
        stored = text
        saved += text
    }
}

/** In-memory [SunsetKeyValueStore]. */
internal class MapSunsetStore(val values: MutableMap<String, String> = mutableMapOf()) : SunsetKeyValueStore {
    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }
}
