package com.abominableenterprise.cherkit.sunset

/**
 * Accepts only plain `https` store links, with the same rules as Swift's `SunsetStoreURL`.
 *
 * Valid: `https://` (any case); a host of ASCII letters, digits, `.` and `-` (no user info, no IP literal), with an
 * optional port of 1 to 5 digits; then a path, query and fragment using only RFC 3986 URL characters (letters,
 * digits, `-._~:/?@!$&'()*+,;=`, `%` plus two hex digits, and at most one `#`).
 */
internal object SunsetStoreUrl {
    private const val SCHEME = "https://"
    private const val URL_PUNCTUATION = "-._~:/?@!$&'()*+,;="

    fun isValid(text: String): Boolean {
        if (text.length <= SCHEME.length || !text.startsWith(SCHEME, ignoreCase = true)) return false
        val rest = text.substring(SCHEME.length)
        val authorityEnd = rest.indexOfFirst { it in "/?#" }.let { if (it < 0) rest.length else it }
        return isValidAuthority(rest.substring(0, authorityEnd)) && isValidPathQueryFragment(rest.substring(authorityEnd))
    }

    private fun isValidAuthority(authority: String): Boolean {
        val host = authority.substringBefore(':')
        if (host.isEmpty() || !host.all { isAsciiAlphanumeric(it) || it == '.' || it == '-' }) return false
        if (':' !in authority) return true
        val port = authority.substringAfter(':')
        return port.length in 1..5 && port.all { it in '0'..'9' }
    }

    private fun isValidPathQueryFragment(text: String): Boolean {
        var index = 0
        var fragments = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '%') {
                if (index + 2 >= text.length || !isHexDigit(text[index + 1]) || !isHexDigit(text[index + 2])) return false
                index += 3
                continue
            }
            if (char == '#') {
                fragments += 1
                if (fragments > 1) return false
            } else if (!isAsciiAlphanumeric(char) && char !in URL_PUNCTUATION) {
                return false
            }
            index += 1
        }
        return true
    }

    private fun isAsciiAlphanumeric(char: Char): Boolean = char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9'

    private fun isHexDigit(char: Char): Boolean = char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'
}
