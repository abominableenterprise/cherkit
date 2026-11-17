update-compose: fixed

- Added a public `PlayUpdatePrompt(state, isAllowed, ...)` overload for a synchronous live veto after the Play request, immediately before starting the sheet. Read current app state inside the callback to prevent a busy interaction or modal from racing the next Compose frame. A veto returns `SUPPRESSED` without consuming the session; existing overloads remain source- and binary-compatible.
- Exported Play In-App Updates as an Android API dependency so consumers can name `AppUpdateManager` and `FakeAppUpdateManager` without declaring a duplicate dependency.
- Documented process-wide `UpdateSession.shared` in prompt tests and using a fresh `UpdateSession()` with `UpdateLauncher.launch` for isolated launch-order tests.
