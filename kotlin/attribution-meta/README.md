# CherKit Attribution Meta — in progress

Placeholder KMP module depending on `:attribution-revenuecat`. There are no public APIs, attribution behavior or
SDK calls yet; only an internal common marker and a trivial common test.

The Android source set declares `com.facebook.android:facebook-core:19.0.1` as **compileOnly**. This is a compile
baseline only. Consuming Android apps must supply the Meta runtime, choose and retain their own SDK versions,
and own initialization and configuration. CherKit does not bundle or publish the vendor SDK as a runtime
dependency. Nothing initializes Meta in this placeholder. Apps using RevenueCat must likewise supply its runtime.

## Publishing variants

Like `telemetry-firebase`, this module uses explicit API mode, Android host tests, a JVM target and Maven publishing.
It publishes root KMP metadata plus `attribution-meta-android` (AAR) and `attribution-meta-jvm` (JAR).
The JVM variant contains only the internal vendor-free marker, not a Meta JVM integration.
