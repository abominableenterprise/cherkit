analytics: added

- Android: `PostHogAnalyticsBackend(context, apiKey, host = "https://us.i.posthog.com")`, an `AnalyticsBackend` backed
  by `posthog-android` 3.61.1. Pass it to `Analytics(...)`; the `:analytics` Android artifact now depends on
  `com.posthog:posthog-android` (the JVM target does not).
- It uses a hardened config: no lifecycle, deep-link, screen-view or push autocapture, no feature flags,
  no default person properties, `PersonProfiles.NEVER`, no session replay, no surveys, no error autocapture. Only
  event names are sent; it never calls `identify`, person properties, groups or aliases.
- The SDK client is created on the first `start()` and opted out, closed and dropped on `stop()`. A blank API key
  makes `start()` throw, which `Analytics` swallows, so analytics just stays off.
- The API key is your PostHog project's public client key. Do not enable autocapture, session replay, surveys or
  feature flags in the PostHog project.
