analytics: added

- `PostHogAnalyticsBackend(apiKey:host:)` (Swift, iOS and macOS): the PostHog adapter for `Analytics`. Pass it as the
  backend, e.g. `Analytics(backend: PostHogAnalyticsBackend(apiKey: key), buildEnabled: !isDebug &&
  !AnalyticsTestHost.isRunningTests)`. `host` defaults to `https://us.i.posthog.com`.
- It uses a hardened PostHog config (no autocapture, swizzling, feature-flag preload, person profiles,
  session replay or surveys) and only sends event names. A blank key keeps analytics off.
- It owns `PostHogSDK.shared`: remove any other PostHog setup from your app when you adopt it.
