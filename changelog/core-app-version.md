core: added

- New module `CherKitCore` (Swift library product) / `:core` (Kotlin, `com.abominableenterprise.cherkit.core`) with
  `AppVersion`: parse store-style versions with `AppVersion("1.2.3")` (Swift) or `AppVersion.parse("1.2.3")` (Kotlin)
  and compare them with `<`, `==`, `compareTo`. Whitespace, a leading `v` and a ` (45)`, `+45` or `-beta` suffix are
  ignored; missing components count as 0 (`1.2 == 1.2.0`); malformed input returns `nil` / `null`.
- `CherKitSunset` and `CherKitWhatsNew` (and Kotlin `:sunset`, `:whatsnew`, via `api`) now depend on Core.
