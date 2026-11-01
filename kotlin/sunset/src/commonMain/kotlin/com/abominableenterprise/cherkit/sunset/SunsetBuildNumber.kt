package com.abominableenterprise.cherkit.sunset

/**
 * A build number such as `123` or `4.5.6` (`CFBundleVersion`, the TestFlight `latestBuild`), compared component by
 * component.
 *
 * Strict on purpose: one or more groups of 1 to [MAX_COMPONENT_DIGITS] ASCII digits joined by single dots, nothing
 * else (no spaces, signs, letters or suffixes). Anything else does not parse, and callers then show no prompt. Missing
 * components count as 0, so `12 == 12.0`. The same rules as Swift's `SunsetBuildNumber`.
 */
public class SunsetBuildNumber private constructor(
    /** Numeric components in order, e.g. `[4, 5, 6]` for `"4.5.6"`. Never empty, never negative. */
    public val components: List<Int>,
) : Comparable<SunsetBuildNumber> {
    override fun compareTo(other: SunsetBuildNumber): Int {
        for (index in 0 until maxOf(components.size, other.components.size)) {
            val lhs = components.getOrElse(index) { 0 }
            val rhs = other.components.getOrElse(index) { 0 }
            if (lhs != rhs) return if (lhs < rhs) -1 else 1
        }
        return 0
    }

    /** Equal when [compareTo] is 0, so trailing zero components do not matter. */
    override fun equals(other: Any?): Boolean = other is SunsetBuildNumber && compareTo(other) == 0

    override fun hashCode(): Int = components.dropLastWhile { it == 0 }.hashCode()

    /** The components joined with dots, e.g. `4.5.6`; leading zeros are not kept. */
    override fun toString(): String = components.joinToString(".")

    public companion object {
        /** Largest number of digits in one component, so every component fits in an [Int]. */
        public const val MAX_COMPONENT_DIGITS: Int = 9

        /** Parses [text], or returns `null` when it is not a valid build number. */
        public fun parse(text: String): SunsetBuildNumber? {
            val components = text.split('.').map { part ->
                if (part.length !in 1..MAX_COMPONENT_DIGITS || !part.all { it in '0'..'9' }) return null
                part.toInt()
            }
            return SunsetBuildNumber(components)
        }
    }
}
