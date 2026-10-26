# CherKitAnalytics (Swift)

The typed, privacy-first analytics facade. Privacy rules, gate and lifecycle are shared with Kotlin and described in
[kotlin/analytics/README.md](../../kotlin/analytics/README.md); the behavior is specified in
[fixtures/analytics](../../fixtures/analytics).

```swift
import CherKitAnalytics

enum ExampleEvent: String, AnalyticsEvent { // the app's allowlist; `name` comes from the raw value
    case appOpened = "app_opened"
    case itemAdded = "item_added"
}

#if DEBUG
let isReleaseBuild = false
#else
let isReleaseBuild = true
#endif

@MainActor let analytics = Analytics<ExampleEvent>(
    backend: myProviderBackend,               // an AnalyticsBackend, e.g. the PostHog adapter
    buildEnabled: isReleaseBuild && !AnalyticsTestHost.isRunningTests
)

analytics.consentGranted = settings.analyticsOptIn    // default false: opt-in
analytics.sessionActive = session.isSignedInAdult     // never true on a kid's device
analytics.track(.appOpened)                           // returns whether it was sent
```

`Analytics` is `@MainActor`: its state and every backend call stay on the main thread without locks, which suits
UI-driven events. From background code, hop with `Task { @MainActor in analytics.track(.itemAdded) }`.

## PostHog on iOS

`PostHogAnalyticsBackend` (iOS and macOS only; it compiles to nothing on Linux) is the PostHog adapter:

```swift
import CherKitAnalytics

#if DEBUG
let isDebug = true
#else
let isDebug = false
#endif

@MainActor let analytics = Analytics<ExampleEvent>(
    backend: PostHogAnalyticsBackend(apiKey: "phc_..."),   // host defaults to https://us.i.posthog.com
    buildEnabled: !isDebug && !AnalyticsTestHost.isRunningTests
)

analytics.consentGranted = settings.analyticsOptIn
analytics.sessionActive = session.isSignedInAdult
```

- The project key is PostHog's public client key, so it is fine to ship in the app. A blank key keeps analytics off.
- The backend owns `PostHogSDK.shared`: don't set it up or call it elsewhere in the app.
- It uses a hardened config: no lifecycle, screen-view, element, push-notification or error autocapture,
  no swizzling, no feature-flag preload or `$feature_flag_called` events, no person profiles or default person
  properties, no session replay, no surveys. Only `capture(name)` is ever called, without properties; never
  `identify`, `alias`, `group` or `register`.
- Keep autocapture, session replay, surveys and feature flags off in the PostHog project settings too.
- When the gate opens, the backend sets the SDK up (if needed) and opts in. When it closes, the backend opts out and
  closes the SDK. The opt-out is persisted, so nothing is sent after a relaunch until the gate opens again.
  The next open sets up the same shared instance again: in posthog-ios 3.71.4, `close()` clears the instance and
  allows a fresh `setup`.
