package com.abominableenterprise.cherkit.sunset

/** How the running build was installed. It picks which "newer build" sources apply (see [SunsetDecider.decide]). */
public enum class SunsetChannel {
    /**
     * A release install from the App Store or Google Play (the default): the config and, when enabled, the App Store
     * lookup can offer an update.
     */
    STORE,

    /** A TestFlight beta install: the config's `testFlight` block can offer a newer test build. Never gets lookups. */
    TEST_FLIGHT,

    /** Anything else (debug, emulator, sideload): only the config applies, as before channels existed. */
    OTHER,
}
