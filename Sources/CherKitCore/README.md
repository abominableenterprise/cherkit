# CherKit Core

Tiny shared helpers (`AppVersion`) used by CherKitSunset and CherKitWhatsNew. Foundation only.

**`MemberImportVisibility`:** Sunset and WhatsNew hand you `AppVersion` values (e.g. `WhatsNewEntry.version`,
`SunsetPrompt.latestVersion`). If your target enables Swift's `MemberImportVisibility` upcoming feature, using
`AppVersion` members (e.g. `entry.version.description`) needs `import CherKitCore` in that file. Without the import,
use `String(describing: entry.version)`.

Swift: `Sources/CherKitCore`, tests in `Tests/CherKitCoreTests`. Kotlin: `kotlin/core`. Shared test cases: `fixtures/core`.
Test with `scripts/test.sh core`.
See [kotlin/core/README.md](../../kotlin/core/README.md), the module README shared by both platforms.
