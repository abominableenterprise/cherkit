publishing: added

- The Kotlin modules are published through JitPack from release tags. Add `maven("https://jitpack.io")` to your
  repositories and depend on `com.github.abominableenterprise.cherkit:<module>:vX.Y.Z`, where `<module>` is
  `analytics`, `sunset`, `whatsnew` or `core`. Gradle picks the Android (AAR) or JVM variant itself.
- The README's new Install section covers SwiftPM (`https://github.com/abominableenterprise/cherkit`, `from: "0.1.0"`)
  and Gradle.
