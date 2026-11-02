package com.abominableenterprise.cherkit.telemetry

import com.abominableenterprise.cherkit.core.AppVersion

internal object TelemetryValidation {
    private val name = Regex("[a-z][a-z0-9_.]{0,63}")
    private val string = Regex("[a-z0-9_.-]{1,64}")

    fun validName(value: String): Boolean = name.matches(value)

    fun validValue(value: Any?): Boolean = when (value) {
        is Boolean, is Int, is AppVersion -> true
        is Double -> value.isFinite()
        is String -> string.matches(value)
        else -> false
    }

    fun attributes(values: Map<String, Any>): Map<String, Any> =
        values.filter { (key, value) -> validName(key) && validValue(value) }
}
