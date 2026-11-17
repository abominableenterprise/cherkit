# Changelog

Developer-facing notes on CherKit for the apps that use it. Versions follow semver; releases are git tags `vX.Y.Z`
(SwiftPM resolves the tag, JitPack publishes the Kotlin modules with the tag as the version). Between releases,
changes collect as fragments in [changelog/](changelog/README.md).

## 0.9.2

Patch release: source-compatible Android Compose fixes (additive overloads only). Swift is unchanged;
the same `v0.9.2` tag also resolves for SwiftPM.

### Kotlin / Android

- fixed: Review Compose and Update Compose export Play Review and In-App Updates as API dependencies, so
  consumers can use `ReviewManager`, `AppUpdateManager` and their test fakes without duplicate dependencies.
- fixed: `ReviewPromptEffect(coordinator, isIdle: () -> Boolean, ...)` reads live state before and after Play's
  suspended request. It rechecks the composition's resumed lifecycle and bound Activity before consuming a
  prompt; stopped roots and finishing/destroyed Activities cannot consume it, including direct `PlayReview.launch`
  calls. Existing Boolean overloads and compiled callers remain supported. See the [Review guide](kotlin/review-compose/README.md).
- fixed: `PlayUpdatePrompt(state, isAllowed, ...)` adds a synchronous live veto immediately before starting the
  Play sheet. A veto returns `SUPPRESSED` without consuming the session; existing overloads remain source- and
  binary-compatible. Use a fresh `UpdateSession()` with `UpdateLauncher.launch` for isolated tests rather than
  process-wide `UpdateSession.shared`. See the [Update guide](kotlin/update-compose/README.md).
- fixed: Checker-based `WhatsNewPrompt` adds optional `onDismiss`, called exactly once immediately after
  `markSeen()` on user dismissal, to clear pending-notes gates. `onVisibilityChange` remains presentation-only
  (including suppression and leaving composition without consuming notes). Both previous compiled signatures
  remain supported through hidden compatibility overloads. See the [WhatsNew guide](kotlin/whatsnew-compose/README.md).

## 0.9.1

Patch release: all Kotlin/Android artifacts target Java 17 bytecode (class version 61). Consumers need JDK 17+
for JVM/Robolectric tests. No API changes; Swift is unchanged, and the same `v0.9.1` tag resolves for SwiftPM.

### Kotlin / Android

- fixed: JDK 17 test hosts can load CherKit; 0.9.0 artifacts used Java 21 bytecode (class version 65) and failed
  to load. See the [install guide](README.md#android--jvm-gradle-via-jitpack).

### Build / Docs

- changed: Linux setup reuses Gradle downloads across release/module edits, skips warm-up when both caches are
  available, and exits quickly on configured VMs. Set `CHERKIT_GRADLE_WARMUP=1` to force compilation. Tooling only.

Older releases: [0.9.0 and earlier](CHANGELOG-archive.md).
