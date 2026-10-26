# fixtures

Shared JSON test cases: the cross-platform contract. Both the Swift tests (via `Tests/CherKitTestSupport/Fixtures.swift`)
and the Kotlin JVM tests (via the `cherkit.fixtures` system property set in the root `build.gradle.kts`) read
`fixtures/<module>/*.json`, so iOS and Android cannot drift. Specify shared behavior here first, then implement it on
both platforms. Each module owns only its own folder.
