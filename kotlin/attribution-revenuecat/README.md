# CherKit Attribution RevenueCat — in progress

Placeholder KMP module depending on `:core`. There are no public APIs, attribution behavior or SDK calls yet;
only an internal common marker and a trivial common test.

The Android source set declares `com.revenuecat.purchases:purchases:9.29.1` as **compileOnly**. This is a
9.x compile baseline, not the upstream latest major. Consuming Android apps must supply the RevenueCat runtime,
choose and retain their own SDK versions, and own initialization and configuration. CherKit does not bundle or
publish the vendor SDK as a runtime dependency. Nothing initializes RevenueCat in this placeholder.

## Publishing variants

Like `telemetry-firebase`, this module uses explicit API mode, Android host tests, a JVM target and Maven publishing.
It publishes root KMP metadata plus `attribution-revenuecat-android` (AAR) and `attribution-revenuecat-jvm` (JAR).
The JVM variant contains only the internal vendor-free marker, not a RevenueCat JVM integration.
