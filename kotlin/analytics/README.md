# CherKit Analytics

A small typed facade apps call instead of a provider SDK. Each app owns its allowlist of events; the provider
sits behind `AnalyticsBackend` (on Android: `PostHogAnalyticsBackend`). Swift: `Sources/CherKitAnalytics`
([Swift usage](../../Sources/CherKitAnalytics/README.md)). Kotlin: `kotlin/analytics`. Shared cases:
`fixtures/analytics` (`gate.json`, `lifecycle.json`). Test with `scripts/test.sh analytics`.

## Privacy rules

- **No properties, only names.** There is no API that accepts event properties, user ids or traits, so no PII can be
  attached to an event.
- **Allowlisted names only.** Events are an app-owned type. A name must match `^[a-z][a-z0-9_]{0,63}$` (ASCII);
  anything else is dropped. Check your whole allowlist in a test with `AnalyticsEventName.isValid`.
- **Opt-in.** `consentGranted` starts `false`; nothing is sent or started until the app sets it from the user's choice.
- **Off in debug, tests and emulators.** The app passes `buildEnabled = false` there (`AnalyticsTestHost` helps).
- **Off on kids' devices.** `sessionActive` is set only for a session allowed to be measured, e.g. a signed-in adult.
  Apps never set it on a child or kid device.

## Gate and lifecycle

An event is sent only when `buildEnabled && consentGranted && sessionActive` and its name is valid. The backend is
started the first time the gate opens, stopped (opt out and close) whenever it closes, and started again if it
re-opens; nothing is captured while it is closed. `track` returns whether the event was handed to the backend.
Analytics is best-effort: backend exceptions are swallowed and never reach app code. A `start()` that throws is
followed at once by `stop()`, so a partly started SDK is opted out again.

## Kotlin usage

```kotlin
enum class ExampleEvent(override val eventName: String) : AnalyticsEvent {
    APP_OPENED("app_opened"),
    ITEM_ADDED("item_added"),
}

val analytics = Analytics<ExampleEvent>(
    backend = myProviderBackend,  // an AnalyticsBackend, e.g. the PostHog adapter
    buildEnabled = !BuildConfig.DEBUG && !AnalyticsTestHost.isRunningTests && !isEmulator,
)

analytics.consentGranted = settings.analyticsOptIn  // default false: opt-in
analytics.sessionActive = session.isSignedInAdult   // never true on a kid's device
analytics.track(ExampleEvent.APP_OPENED)            // returns whether it was sent
```

Kotlin uses `eventName` because an enum's `name` is final. `Analytics` is not thread-safe, matching the Swift
`@MainActor` design: use one instance from the main thread.

## Android setup (PostHog)

`PostHogAnalyticsBackend(context, apiKey, host = "https://us.i.posthog.com")` (Android only; the JVM target has no
PostHog dependency) wraps `posthog-android` with a hardened config: no lifecycle, deep-link, screen-view
or push autocapture, no feature-flag preload or `$feature_flag_called` events, no default person properties,
`PersonProfiles.NEVER`, no session replay, no surveys, no error autocapture. It only ever calls `optIn`,
`capture(name)` with no properties, `optOut` and `close`; never `identify`, person properties, groups or aliases.
The SDK client is created on the first `start()` and dropped on `stop()`, so a re-opened gate gets a fresh client.

```kotlin
class ExampleApp : Application() {
    lateinit var analytics: Analytics<ExampleEvent>

    override fun onCreate() {
        super.onCreate()
        analytics = Analytics(
            PostHogAnalyticsBackend(this, apiKey = BuildConfig.POSTHOG_KEY),  // host defaults to US cloud
            buildEnabled = !BuildConfig.DEBUG,
        )
        analytics.consentGranted = settings.analyticsOptIn  // opt-in, default false
        analytics.sessionActive = session.isSignedInAdult   // never on a kid's device
    }
}
```

- **The API key** is the PostHog project key: a public client key the app supplies (e.g. via `BuildConfig`); CherKit
  holds no secrets. A blank key makes `start()` throw `IllegalArgumentException`; `Analytics` swallows it, rolls back
  and simply sends nothing, so the app keeps working.
- **Keep the PostHog project locked down too:** apps must not enable autocapture, session replay, surveys or feature
  flags in the PostHog project settings. The client config turns them off, but remote settings must not reintroduce
  them.
