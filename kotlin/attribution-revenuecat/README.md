# CherKit Attribution RevenueCat

Optional RevenueCat setup adapter for Android. Replace a consumer app's existing `Purchases.configure`
call with one CherKit call. Purchases, identities, entitlements and purchase UI remain RevenueCat-owned.
There is no central CherKit registry and no Meta SDK dependency.

## Install and migrate

Add JitPack (`https://jitpack.io`) to the app's repositories, then pin the CherKit release and supply
RevenueCat explicitly:

```kotlin
implementation("com.github.abominableenterprise.cherkit:attribution-revenuecat:<cherkit-tag>")
implementation("com.revenuecat.purchases:purchases:9.29.1")
```

RevenueCat **9.29.1** is the compile/test baseline, not a claim to support every SDK version or the
latest major. It is `compileOnly` in the Android library: CherKit does not bundle the vendor runtime
or force its version on consumers. Keep a compatible runtime in the app and verify before upgrading.

At the original configure site in `Application.onCreate`, before purchase UI:

```kotlin
import com.abominableenterprise.cherkit.attribution.revenuecat.Audience
import com.abominableenterprise.cherkit.attribution.revenuecat.RevenueCatAttribution
import com.abominableenterprise.cherkit.attribution.revenuecat.RevenueCatSetup
import com.revenuecat.purchases.EntitlementVerificationMode
import com.revenuecat.purchases.PurchasesAreCompletedBy

RevenueCatAttribution.start(
    application = this,
    revenueCat = RevenueCatSetup(
        apiKey = revenueCatPublicKey,
        appUserId = existingUserId, // null retains RevenueCat anonymous-user behavior
    ) {
        // Move the app's existing native builder options here, unchanged.
        purchasesAreCompletedBy(PurchasesAreCompletedBy.REVENUECAT)
        entitlementVerificationMode(EntitlementVerificationMode.INFORMATIONAL)
    },
    audience = Audience.AdultOnly,
)
```

Use the app's existing values, not the sample options above. The receiver is the actual
`PurchasesConfiguration.Builder`, not a reduced CherKit option list: store, executor, observer mode /
`purchasesAreCompletedBy`, verification, diagnostics, in-app messages, locale and other native
options remain available. `apiKey` initializes the native builder; `appUserId` is applied first, then
the lambda (so its `appUserID(...)` wins). The sole enforced override is
`automaticDeviceIdentifierCollectionEnabled(false)`, applied **after** the lambda. Apple-specific
options such as a user-defaults suite belong to the Swift adapter, not this Android SDK.

Remove the old `Purchases.configure` call; never call both. Keep normal `Purchases.sharedInstance`
purchase, login/logout, restore, entitlement and UI calls unchanged. No new subscriber or identity
migration is introduced. Remove any old broad device-identifier collection or attribution glue.
The builder lambda must be side-effect free: it runs only when configuration is actually needed.

The adapter serializes starts in this process and configures once. Repeated/concurrent starts ignore
later keys, identities and options. If RevenueCat was configured earlier by another component, it is
left untouched and the lambda is skipped. **That is not a privacy retrofit:** existing settings,
identifiers and vendor work are not cleared or disabled. Consolidate initialization at this one
site; unrelated direct SDK initialization must not race it. Use the native login/logout APIs for
later identity changes, not another `start`.

## Audience and coverage

- `Audience.AdultOnly` means an **audited deployment property**, not the current user's role,
  age assertion, ATT permission or analytics consent.
- `Audience.RestrictedOrSwitchable` covers restricted, unknown or switchable audiences. It enables
  purchases only; do not promote an individual adult session to `AdultOnly` in a switchable app.
- Android configures purchases only for **both** values, including debug/tests/emulators. It never
  calls Apple APIs. The shared vendor-free gate models the Apple policy for cross-platform tests:
  only iOS + adult-only + non-debug + non-test-host + non-simulator is eligible. macOS is ineligible.
- The companion Swift adapter provides eligible iOS Apple Search Ads AdServices attribution through
  RevenueCat. Connect Apple Ads in the **RevenueCat dashboard** for campaign names and revenue/LTV
  reporting. RevenueCat owns token exchange and retries; this Kotlin module handles no tokens.
- Android customer-level campaign revenue, Google Ads, Play referrers, tagged links, Meta, SKAN /
  AdAttributionKit and a full MMP are **not covered**. Missing campaign evidence is unknown, not proof
  of organic acquisition. Successful SDK setup does not prove campaign matching or reporting.

## Privacy and pinned capabilities

In the 9.29.1 Android SDK, automatic device-identifier collection defaults to true. This adapter
explicitly turns it off before `Purchases.configure`, even if the options lambda requests true.
It never calls `collectDeviceIdentifiers`, reads advertising IDs, exports campaign attributes,
collects referrers, sends events to PostHog, or installs lifecycle observers. It adds no attribution
work for restricted audiences; ordinary RevenueCat purchase processing still runs.

This is not a blanket guarantee about an app's other SDKs, existing subscriber attributes or dashboard
integrations. Audit those separately. Do not inherit marketing/PII forwarding accidentally. No ATT
prompt is added and analytics opt-in is not advertising authorization.

Android has no Apple attribution route or local attribution queue to stop. An existing SDK instance
cannot be reconfigured safely here; previously stored identifiers, pending vendor work and
server-side forwarding are not cancelled by changing audience or removing the adapter. This API has
no `stop()` or revocation guarantee. Do not enable unsafe switchable routes. Apple persistence,
offline retries, adult-to-restricted transitions and real-device campaign coverage require the
companion Swift capability audit; JVM policy tests are not evidence of those SDK behaviors.

## Tests and published variants

- `commonTest`: recording SDK fake checks once-only configuration, check/build/configure order,
  exact configuration-object pass-through, already-configured handling and configuration failure.
- `jvmTest`: runs `fixtures/attribution/gates.json` using the root-provided `cherkit.fixtures` path,
  shared with Swift. No Android or vendor runtime is needed for the policy/coordinator tests.
- `androidHostTest`: Robolectric + the pinned SDK checks native builder option/identity pass-through,
  lambda ordering and the forced privacy setting. It does not initialize a live RevenueCat client
  or establish production attribution coverage.

Gradle tasks: `:attribution-revenuecat:jvmTest` and `:attribution-revenuecat:testAndroidHostTest`.
Real purchase/account/offline/reinstall and dashboard attribution smoke tests remain app-owned.

Publishing includes root KMP metadata, `attribution-revenuecat-android` (AAR) and
`attribution-revenuecat-jvm` (JAR). Common/JVM exposes `Audience` and contains vendor-free internal
policy/coordinator code only; `RevenueCatSetup` and `RevenueCatAttribution.start` are Android-only.
KMP apps use the Swift adapter in their iOS app, not a Kotlin Apple SDK integration.
