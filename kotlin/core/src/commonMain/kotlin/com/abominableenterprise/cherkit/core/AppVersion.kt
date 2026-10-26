package com.abominableenterprise.cherkit.core

/**
 * A store-style app version such as `1.2.3`, compared component by component.
 *
 * Parse with [AppVersion.parse]. Surrounding ASCII whitespace, a leading `v`/`V`, and a build or pre-release suffix
 * that starts with a space, `+` or `-` (`1.2.3 (45)`, `1.2.3+45`, `1.2.3-beta`) are accepted; the suffix is dropped.
 * Anything else returns `null`, and callers should fail open on `null`. Missing components count as 0, so
 * `1.2 == 1.2.0` (for both [equals] and [compareTo]). The contract is `fixtures/core/version.json`, shared with
 * Swift's `AppVersion`.
 */
@ConsistentCopyVisibility
public data class AppVersion private constructor(
    /** Numeric components in order, e.g. `[1, 2, 3]` for `"1.2.3"`. Never empty, never negative. */
    public val components: List<Int>,
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        for (index in 0 until maxOf(components.size, other.components.size)) {
            val lhs = components.getOrElse(index) { 0 }
            val rhs = other.components.getOrElse(index) { 0 }
            if (lhs != rhs) return if (lhs < rhs) -1 else 1
        }
        return 0
    }

    /** Equal when [compareTo] is 0, so trailing zero components do not matter. */
    override fun equals(other: Any?): Boolean = other is AppVersion && compareTo(other) == 0

    override fun hashCode(): Int = components.dropLastWhile { it == 0 }.hashCode()

    /** The numeric components joined with dots, e.g. `1.2.3`; the suffix and leading zeros are not kept. */
    override fun toString(): String = components.joinToString(".")

    public companion object {
        /** Largest number of digits accepted in one component, so every component fits in an [Int]. */
        public const val MAX_COMPONENT_DIGITS: Int = 9

        private const val SUFFIX_SEPARATORS = " +-"

        /** Parses [string], or returns `null` when it is not a valid version. */
        public fun parse(string: String): AppVersion? {
            // ASCII-only trimming keeps the rules identical to Swift's, whose Unicode whitespace set differs.
            var text = string.trim(::isAsciiWhitespace)
            if (text.startsWith('v') || text.startsWith('V')) text = text.substring(1)

            val components = mutableListOf<Int>()
            var current = 0
            var digits = 0
            for (char in text) {
                if (char in '0'..'9') {
                    digits += 1
                    if (digits > MAX_COMPONENT_DIGITS) return null
                    current = current * 10 + (char - '0')
                    continue
                }
                // Every non-digit ends a component, so it must follow at least one digit.
                if (digits == 0) return null
                components += current
                current = 0
                digits = 0
                if (char == '.') continue
                if (char !in SUFFIX_SEPARATORS) return null
                return AppVersion(components)
            }
            if (digits == 0) return null
            components += current
            return AppVersion(components)
        }

        private fun isAsciiWhitespace(char: Char): Boolean = char == ' ' || char in '\u0009'..'\u000D'
    }
}
