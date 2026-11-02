package com.abominableenterprise.cherkit.telemetry.firebase

import com.abominableenterprise.cherkit.core.AppVersion

internal object FirebaseValues {
    private val name = Regex("[a-z][a-z0-9_.]{0,63}")
    private val token = Regex("[a-z0-9_.-]{1,64}")
    private val type = Regex("[A-Za-z][A-Za-z0-9_.$]{0,127}")
    private val traceKey = Regex("[A-Za-z][A-Za-z_]{0,39}")

    fun name(value: String): Boolean = name.matches(value)
    fun type(value: String): String = if (type.matches(value)) value else "Error"
    fun domain(value: String?): String? = value?.takeIf { name(it) }
    fun traceKey(value: String): Boolean = traceKey.matches(value) &&
        !value.startsWith("firebase_", true) && !value.startsWith("google_", true) &&
        !value.startsWith("ga_", true)

    fun value(value: Any): String? = when (value) {
        is Boolean, is Int, is AppVersion -> value.toString()
        is Double -> if (value.isFinite()) value.toString() else null
        is String -> value.takeIf { token.matches(it) }
        else -> null
    }

    fun attributes(values: Map<String, Any>): Map<String, String> = values.mapNotNull { (key, value) ->
        if (name(key)) value(value)?.let { key to it } else null
    }.toMap()
}
