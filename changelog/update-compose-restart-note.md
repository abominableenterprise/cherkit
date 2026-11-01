update-compose: changed

- Docs: `PlayUpdateState.completeUpdate()` ("Restart to install") restarts the app process at once. Apps that queue
  local writes (e.g. an offline database or a sync queue) should flush or confirm them first, or disable or delay the
  restart button until nothing is pending. The cold-start recipe now also gates Play's sheet on Sunset's running
  checks and the "What's new" launch check:
  `suppressed = !LaunchGate.sunsetSettled || sunsetKind != null || sunsetChecking || !LaunchGate.whatsNewSettled || whatsNewVisible`.
  No behavior change.
