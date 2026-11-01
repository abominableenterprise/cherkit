package com.abominableenterprise.cherkit.sunset

import kotlin.time.Instant

/**
 * Parses the UTC timestamps of the hosted config (`notBefore`), with the same rules as Swift's `SunsetTimestamp`.
 *
 * Accepted: exactly `YYYY-MM-DDTHH:MM:SSZ`, optionally with `.` and 1 to 9 fraction digits before the `Z`, as written
 * by `date -u +%Y-%m-%dT%H:%M:%SZ`. The date must exist; seconds run 0 to 59. Anything else returns `null`.
 */
internal object SunsetTimestamp {
    fun parse(text: String): Instant? {
        if (text.length < 20 || text.last() != 'Z') return null
        if (text[4] != '-' || text[7] != '-' || text[10] != 'T' || text[13] != ':' || text[16] != ':') return null
        val year = number(text, 0, 4) ?: return null
        val month = number(text, 5, 2) ?: return null
        val day = number(text, 8, 2) ?: return null
        val hour = number(text, 11, 2) ?: return null
        val minute = number(text, 14, 2) ?: return null
        val second = number(text, 17, 2) ?: return null
        if (month !in 1..12 || day !in 1..daysIn(month, year) || hour >= 24 || minute >= 60 || second >= 60) return null
        var nanoseconds = 0
        if (text.length > 20) {
            val digits = text.length - 21
            if (text[19] != '.' || digits !in 1..9) return null
            val fraction = number(text, 20, digits) ?: return null
            nanoseconds = fraction * pow10(9 - digits)
        }
        val seconds = daysFromCivil(year, month, day).toLong() * 86_400 + hour * 3_600 + minute * 60 + second
        return Instant.fromEpochSeconds(seconds, nanoseconds)
    }

    private fun number(text: String, start: Int, count: Int): Int? {
        var value = 0
        for (char in text.substring(start, start + count)) {
            if (char !in '0'..'9') return null
            value = value * 10 + (char - '0')
        }
        return value
    }

    private fun pow10(exponent: Int): Int = (0 until exponent).fold(1) { acc, _ -> acc * 10 }

    private fun daysIn(month: Int, year: Int): Int = when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    /** Days since 1970-01-01 in the proleptic Gregorian calendar (H. Hinnant's `days_from_civil`). */
    private fun daysFromCivil(year: Int, month: Int, day: Int): Int {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yearOfEra = y - era * 400
        val dayOfYear = (153 * (month + (if (month > 2) -3 else 9)) + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146_097 + dayOfEra - 719_468
    }
}
