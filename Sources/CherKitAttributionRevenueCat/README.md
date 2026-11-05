# RevenueCat attribution (Apple)

An optional one-call replacement for RevenueCat configuration in a consumer app.
Import `CherKitAttributionRevenueCat` instead of adding a second Purchases instance.
The SDK owns purchases, token exchange and retries; CherKit never reads a raw attribution token.

## Replace configuration

```swift
import CherKitAttributionRevenueCat

// At the existing configure site, on the main actor, before purchase UI.
RevenueCatAttribution.start(
    revenueCat: .init(apiKey: revenueCatPublicKey, appUserID: existingUserID),
    audience: .adultOnly
)
```

Delete the old `Purchases.configure` call; never call both. Login, logout, purchase,
restore, entitlement and UI calls remain the app's own RevenueCat calls.
For an existing native builder, move its options into the setup closure:

```swift
RevenueCatAttribution.start(
    revenueCat: .init(apiKey: revenueCatPublicKey, appUserID: existingUserID) { builder in
        _ = builder.with(purchasesAreCompletedBy: .myApp, storeKitVersion: .storeKit2)
            .with(entitlementVerificationMode: .informational)
            .with(userDefaults: existingDefaults)
    },
    audience: .restrictedOrSwitchable
)
```

The native builder passes through all existing options (observer/purchase completion,
verification, defaults suite, StoreKit version, timeouts, etc.). Automatic device-identifier
collection is the intentional exception: CherKit sets it **false after** the closure.
Do not initialize the SDK, collect identifiers or enable attribution inside that closure.
Remove any old broad identifier collection/attribution startup calls.

Startup is main-actor serialized. Repeated calls are no-ops; a previously configured SDK
is left untouched, including attribution. This is not a way to retrofit privacy onto an
SDK that has already started. Changing options/audience after startup does not reconfigure
purchases or cancel vendor work. All SDK startup must use this one call, not race it.

## Audience and eligibility

`Audience.adultOnly` means an **audited deployment property**, not the current user's role:
no restricted sessions, role switching or unknown audience. Otherwise use
`.restrictedOrSwitchable`, which configures purchases only. Do not upgrade audience at runtime.
Debug builds, XCTest/recognized test hosts, simulators and non-iOS platforms also keep
new Apple attribution activation off. The app cannot override those production guards.
The pure gate and recording-fake startup sequence share all 48 fixture cases with Kotlin.

## Coverage and privacy

On eligible release iOS devices the adapter calls RevenueCat's
`enableAdServicesAttributionTokenCollection()`. Connect Apple Ads in the
[RevenueCat dashboard](https://app.revenuecat.com/) using the
[Apple Search Ads integration recipe](https://www.revenuecat.com/docs/integrations/attribution/apple-search-ads-integration)
for campaign names and Revenue/LTV reporting. Missing evidence means **unknown**, not proven organic.
AdServices does not require an ATT prompt; this is not a consent or store-policy exemption.

No advertising IDs, device-ID collection, custom acquisition attributes, referrer parsing,
raw token handling, analytics export or purchase sender is added. Restricted deployments
get plain purchases only, not new attribution. Existing subscriber attributes and vendor
queues are not erased. No Android campaign-revenue or Google coverage is provided.
This adapter does not integrate other ad networks, aggregate attribution or any server.

## Capability record and adoption checks

Source audit against resolved purchases-ios **5.102.0** (the package allows 5.x):

- [AdServices activation](https://github.com/RevenueCat/purchases-ios/blob/5.102.0/Sources/Purchasing/Purchases/Attribution.swift)
  defaults to an in-memory `false`; enabling sets it to `true` and posts through the SDK.
  There is no public disable counterpart. The adapter never enables an ineligible launch.
- [Configuration](https://github.com/RevenueCat/purchases-ios/blob/5.102.0/Sources/Purchasing/Configuration.swift)
  defaults automatic identifier collection to `true`; the adapter forces `false` before configure.
- [Attribution posting](https://github.com/RevenueCat/purchases-ios/blob/5.102.0/Sources/Attribution/AttributionPoster.swift)
  owns cached sent state, exchange and retries. In-flight/vendor work is not cancellable by this adapter.

This is source evidence, **not** physical-device or dashboard attribution validation.
Before release, pin/audit the actual resolved SDK and privately check adult→restricted,
offline→online and relaunch behavior. Keep switchable deployments OFF: do not assume
skipping an enable call cancels a previously enabled route or pending uploads.
Also check reinstall, existing customer/new device, two devices and anonymous→login;
CherKit writes no customer acquisition fields and does not attempt first-touch locking.
Purchases must continue offline; missing/early attribution remains a reporting gap.
No physical-device campaign claim, dashboard access or retry transition is verified by
unit/simulator tests. Rollback removes the adapter at the configure site in an app release;
past uploads cannot be recalled. No release or tag is included here.
