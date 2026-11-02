telemetry: added

- Added the vendor-free `CherKitTelemetry` Swift product and Kotlin `:telemetry` module for sanitized non-fatal errors, breadcrumbs, spans and crash-context keys.
- Diagnostics default to enabled with explicit build/user gates and a restricted errors-only mode. Invalid names and unsafe attribute values are dropped; backend failures never interrupt the app.
- Added a tiny `TelemetrySink` contract in Core for later module integrations. Firebase adapters and module wiring will follow separately.
