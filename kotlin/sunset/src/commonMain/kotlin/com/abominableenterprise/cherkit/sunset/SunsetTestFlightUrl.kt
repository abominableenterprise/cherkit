package com.abominableenterprise.cherkit.sunset

/**
 * Accepts a `testFlight.url`: `itms-beta://` or `https://` (any case, any host), with the rest following the
 * `storeURL` rules ([SunsetStoreUrl]). The same rules as Swift's `SunsetTestFlightURL`.
 */
internal object SunsetTestFlightUrl {
    private const val BETA_SCHEME = "itms-beta://"

    fun isValid(text: String): Boolean {
        if (text.length > BETA_SCHEME.length && text.startsWith(BETA_SCHEME, ignoreCase = true)) {
            return SunsetStoreUrl.isValid("https://" + text.substring(BETA_SCHEME.length))
        }
        return SunsetStoreUrl.isValid(text)
    }
}
