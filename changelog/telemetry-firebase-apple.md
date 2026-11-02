telemetry: added

- Added the optional `CherKitTelemetryFirebase` Swift product for Crashlytics errors/crashes and iOS Performance spans, compatible with Firebase 11.x through 13.x. Automatic network instrumentation defaults off.
- Added a defaulted `TelemetryBackend.update(restricted:)` hook so adapters can synchronize errors-only sessions without breaking existing implementations. Apps must configure Firebase startup privacy defaults; see the adapter setup guide for SDK revocation limits and dSYM upload instructions.
