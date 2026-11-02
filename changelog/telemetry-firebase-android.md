telemetry: added

- Android apps can use the optional Firebase backend for privacy-filtered non-fatals, crash collection, breadcrumbs and custom keys, with opt-in Performance traces. Apps supply the Firebase BoM, SDKs and Gradle plugins; collection defaults and network instrumentation controls are documented.
- Added a default no-op `updateRestricted(restricted: Boolean)` hook to the Kotlin backend contract, matching Swift's `update(restricted:)` naming, so providers can disable automatic diagnostics immediately in restricted sessions. Existing backend implementations need no changes.
