update-compose: added

- New Android module `com.github.abominableenterprise.cherkit:update-compose`: Google Play In-App Updates (FLEXIBLE
  flow) with `PlayUpdatePrompt(...)`. It runs only on Play installs (Play testing tracks included; sideloads get
  nothing) and is off in debug builds. It starts at most once per session, waits while `suppressed` is true (pass it
  while the Sunset prompt, What's New or another prompt shows), and remembers "Not now" per `availableVersionCode`.
- A downloaded update sets `rememberPlayUpdateState().isDownloaded` (also after a later resume) and calls
  `onDownloaded`; `completeUpdate()` installs it ("Restart to install").
- `onVisibilityChange` reports Play's sheet, e.g. to `ReviewCoordinator.setInterruptionVisible(it,
  UpdateInterruption.SOURCE)` (`"update"`). Opt in: apps that don't add the module are unchanged.
