package com.abominableenterprise.cherkit.whatsnew

/** The string without leading and trailing ASCII whitespace. ASCII-only so Swift and Kotlin trim identically. */
internal fun String.trimAsciiWhitespace(): String = trim { it == ' ' || it in '\u0009'..'\u000D' }
