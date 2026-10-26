# AGENTS.md

Read this before every CherKit task. Module tasks often run in parallel; these rules keep them from conflicting.

## What CherKit is

CherKit is the shared library added to Abominable Enterprise's iOS and Android apps (Kotlin Multiplatform + SwiftUI
apps, pure Swift iOS apps and later others). It ships as a Swift Package (`Package.swift`, iOS 16+ / macOS 13+) and as
Kotlin Multiplatform modules (`kotlin/`, Android + JVM, packages `com.abominableenterprise.cherkit.*`, published through
JitPack as `com.github.abominableenterprise.cherkit:<module>`; see the README's Install section). v1 modules:

- **Analytics** (`CherKitAnalytics`, `:analytics`): thin typed facade over PostHog, fixed list of allowed events, opt-in,
  off in debug builds.
- **Sunset** (`CherKitSunset`, `:sunset`): version sunsetting from a hosted min-version config, soft or hard update screen.
- **WhatsNew** (`CherKitWhatsNew`, `:whatsnew`): "What's new" notes plus the history of versions installed on the device.
- **CherKitUI**: SwiftUI views for Sunset and WhatsNew (Swift only).
- **CherKitCore** / `:core`: tiny shared helpers (AppVersion); changes need their own small PR.

Each module has its own entry point; there is no central `CherKit.configure()` registry, and none should be added.

Public API follows semver. A breaking change (removing or renaming public API, changing behavior apps rely on) needs a
major version bump and a changelog fragment of kind `breaking` that says what breaks and how to migrate.

## Modularity rules

- **Folder = registration.** `settings.gradle.kts` includes every `kotlin/<name>/` that has a `build.gradle.kts`;
  SwiftPM compiles every file under a target's folder. Never edit `settings.gradle.kts`. `Package.swift` already
  declares every planned target; touch it only to add a dependency (in its own PR) or a genuinely new module.
- **One job per file.** Small types, small files.
- **Each module stays inside its own folders:** `Sources/CherKit<Module>`, `Tests/CherKit<Module>Tests`,
  `kotlin/<module>`, `fixtures/<module>`, plus `Sources/CherKitUI/<Module>` for its views.
- **Never edit another module's files in a module PR.** Shared files (`Package.swift`, root Gradle files,
  `gradle/libs.versions.toml`, `scripts/`, `Tests/CherKitTestSupport`, this file) change only in their own small PR.
- **Before editing a shared file** (`Package.swift`, `gradle/libs.versions.toml`, settings/build files, `AGENTS.md`,
  `scripts/`), check open PRs touching it (`gh pr list --search <path>`); if one exists, wait for it or coordinate.
- **A new dependency gets its own small PR** (catalog entry, `Package.swift` dependency, nothing else).
- **Every PR adds a `changelog/<slug>.md` fragment** (format in [changelog/README.md](changelog/README.md)): notes for
  developers of the apps using CherKit. Skip it only for internal changes apps cannot notice: docs, tests, tooling.
  Never edit a central CHANGELOG.
- **Size cap: 800 lines / 50 KB per file**, enforced by `scripts/size-check`. `size-baseline.json` entries may only
  shrink; split the file instead of adding an entry.
- **Logic stays Foundation-only** so it builds and tests on Linux. SwiftUI goes only in `CherKitUI`, wrapped in
  `#if canImport(SwiftUI)`. Kotlin logic lives in `commonMain`; platform code only where unavoidable.
- **Behavior shared across platforms is specified in `fixtures/<module>/` first** as JSON cases, then both the Swift
  and Kotlin tests run those cases. Swift loads them with `Fixtures.decode(_:module:name:)` from
  `Tests/CherKitTestSupport`; Kotlin JVM-based tests read `File(System.getProperty("cherkit.fixtures"), "<module>/...")`
  (set for every Gradle `Test` task by the root `build.gradle.kts`, so put fixture-reading tests in `jvmTest` or
  `androidHostTest`, not `commonTest`).

## Verification

Run, on a Linux VM:

```sh
scripts/setup-linux.sh          # idempotent; installs Swift, JDK, Android SDK; caches Gradle deps with task-cache
scripts/test.sh <module>        # analytics | sunset | whatsnew: size check + that module's Swift and Kotlin tests
scripts/test.sh                 # everything that runs on Linux; run it before opening a PR that touches shared files
```

- `scripts/test.sh` runs `scripts/size-check`, `swift test` and `./gradlew :<module>:jvmTest :<module>:testAndroidHostTest`.
  With a module selected it fails if Swift or Gradle ran 0 tests (a `--filter` that matches nothing still passes on
  its own). At the end it stops the Gradle and Kotlin compile daemons, pass or fail, so no java processes are left.
- Always use large VMs for Linux tasks (4 CPU / 8 GB); `gradle.properties` uses `-Xmx3g` and `test.sh` runs swift +
  Gradle in parallel.
- After a rebase that doesn't touch your module, rerun only that module (`scripts/test.sh <module>`).
- For a one-off probe on a Mac, use `xcodebuild test -only-testing:<Target>/<Test>` (e.g. with
  `-scheme CherKit-Package -destination 'platform=iOS Simulator,name=<iPhone>'`) instead of the full script.
- Hand off right after pushing: hub-merge is on, so don't sit in pr-wait.
- Needs macOS (not verifiable on Linux): `CherKitUI` (its sources compile to nothing on Linux), building for iOS, and
  any iOS simulator check. Linux tasks say in the PR what they could not verify.
- On macOS, `scripts/test-mac.sh [module]` (same module selection as `test.sh`) runs the size check, host `swift build`
  + `swift test`, an iOS Simulator build of every library scheme including `CherKitUI`, the Swift tests on an iPhone
  simulator, and the Kotlin `jvmTest` + Android host tests. Steps without a simulator, JDK 17+ or Android SDK are
  skipped with a message; it ends with a pass/fail summary and timings. Full logs go to `.build/test-mac-logs/`; a
  failed step (and the simulator test step always) prints only its `error:` lines and last 60 lines, plus the log's
  path. Like `test.sh`, it fails a
  selected module whose tests ran 0 times and stops the Gradle and Kotlin daemons at the end. **UI or platform-sensitive PRs** (anything in
  `CherKitUI`, `#if os`/`canImport`/`@available`, `Package.swift` platforms, fixture loading) must be checked with it
  on a macOS task.
- The PR body lists the commands run with their results, and what was not verified.
- Every bug fix comes with a test that fails without the fix.

## Releasing

A release is its own small PR, then a tag:

1. Compile the `changelog/*.md` fragments into a new `## X.Y.Z` section at the top of `CHANGELOG.md`, delete the
   fragments, and bump `version` in `gradle.properties` to `X.Y.Z` (semver; `breaking` fragments mean a major bump).
2. After that PR merges, tag `vX.Y.Z` on `main` and push the tag (SwiftPM consumers resolve it directly).
3. Trigger the JitPack build by requesting `https://jitpack.io/com/github/abominableenterprise/cherkit/vX.Y.Z/`
   (or "Get it" on https://jitpack.io/#abominableenterprise/cherkit). Check its `build.log` there; JitPack publishes
   with the tag as the version, and `jitpack.yml` sets up the JDK and Android SDK.

## Threat model (for rating severity)

- CherKit is client-side library code running inside consumer apps. There is no CherKit server.
- The hosted config and release notes are public, read-only JSON served over HTTPS. A tampered, malformed or unreachable
  config must **fail open**: never lock users out of an app.
- No secrets live in the library. The PostHog project key is a public client key supplied by the app.
- No PII in analytics events. Analytics is off on kids' devices and whenever the user has not opted in.
- Main risks: locking users out (Sunset bugs), privacy leaks (Analytics) and app crashes. Ordinary input validation on
  local data is low severity.

## Review and merge (Paxton's process)

- Small, modular code: one job per file; no source file over ~800 lines / 50 KB; new features in new modules.
- Small PRs: one area, about 500 changed lines or fewer (tests and pure moves excluded). Split bigger work into parts
  ("Part of #N"). Never widen a PR during review. Push after every commit. Bug fixes come with a failing-first test.
- Reviewer: Check Norris (`checknorrisbot[bot]`, status `checknorris`, green only at 5/5). It reviews ~10 quiet minutes
  after the last push (never push empty commits to trigger it). Commands: `@checknorrisbot review`,
  `@checknorrisbot re-review`, `@checknorrisbot rescore`.
- Findings (reply in the thread, mention first, one reply per thread):
  - real P0/P1: fix with a test, `@checknorrisbot Fixed in <sha> (<what changed>)`;
  - false positive: explain citing code (the only reply that clears a P0/P1 besides an owner risk acceptance by Paxton);
  - minor P2: fix if small, else `@checknorrisbot Deferred to #N: <why minor>` into one `follow-up` issue per PR
    (`Follow-ups from #<PR>: <title>`). Answer every P2 before pushing again.
  - Resolve each thread after its verdict.
- **Merge without asking** when, on the current head commit, `checknorris` is green, every review thread is resolved
  and all checks are green: `gh pr merge <n> --merge --match-head-commit <sha>`.
  Never force-push shared branches, never `--delete-branch`.

## The Robots automation (ignore unless you are running inside a The Robots task)

hub-merge: on
hub-merge-requires: checknorris
