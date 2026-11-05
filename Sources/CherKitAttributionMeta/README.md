# Meta attribution (Swift / iOS)

Optional composition of RevenueCat configuration and Meta install/activation attribution
for a consumer app. RevenueCat is the **only Meta purchase sender**. Meta campaign results
are viewed in Ads Manager, not as per-customer campaign revenue in RevenueCat.
Successful CAPI delivery does not establish campaign matching, bidding eligibility or AEM.

## Replace configuration

Link the `CherKitAttributionMeta` Swift Package product. At the existing RevenueCat
configure site, on the main actor and before purchase UI:

```swift
import CherKitAttributionMeta
import CherKitAttributionRevenueCat

MetaRevenueCatAttribution.start(
    revenueCat: RevenueCatSetup(apiKey: revenueCatPublicKey, appUserID: existingUserID),
    audience: Audience.adultOnly
)
```

Public entry points are `MetaRevenueCatAttribution.start(revenueCat: RevenueCatSetup,
audience: Audience)` and `MetaRevenueCatAttribution.stop()`.
Delete the previous `Purchases.configure`, Meta initialization/activation hooks and broad
identifier-collection calls; never run both startup paths. Preserve existing RevenueCat
builder options in `RevenueCatSetup`'s closure; see the [RevenueCat adapter](../CherKitAttributionRevenueCat/README.md).
Automatic device-identifier collection is forced OFF after those options. Login/logout,
purchases, restores, entitlements and purchase UI remain ordinary RevenueCat calls.

`.adultOnly` is an audited, stable **deployment property**, not the current user's role.
Use `.restrictedOrSwitchable` for restricted, switchable or unknown audiences: purchases
continue without new attribution activation. Debug builds, tests, simulators and missing
Meta configuration also leave Meta dormant. Meta calls are iOS-only; this is not a macOS
Meta integration. Missing attribution must never block a purchase.

Eligible order: safe RevenueCat configure → safe runtime Meta settings → initialize Meta
→ read the SDK's actual anonymous ID → RevenueCat `setFBAnonymousID` → Meta `activateApp`.
Refresh the ID on eligible foregrounds and RevenueCat identity changes. Repeated startup
must not stack observers. An already initialized vendor SDK is not made private by calling
this adapter afterward. Do not use repeated startup to change audience policy.
Early purchases and asynchronous identity changes can remain unmatched; no network wait,
purchase replay or attribution backfill is promised.

## Required bundled configuration

The package permits facebook-ios-sdk **19.x** (`19.0.0..<20.0.0`). These exact iOS keys and
runtime properties were audited in the resolved **19.0.1** SDK, not copied from Android.
Re-audit the actual resolved version on upgrades. In the consumer app's **Info.plist**:

```xml
<key>FacebookAppID</key>
<string>$(META_APP_ID)</string>
<key>FacebookClientToken</key>
<string>$(META_CLIENT_TOKEN)</string>
<key>FacebookAutoLogAppEventsEnabled</key>
<false/>
<key>FacebookAdvertiserIDCollectionEnabled</key>
<false/>
<key>FBSDKAutoLogMetaDataEnabled</key>
<false/>
<key>FBSDKAutoLogProductImprovementEnabled</key>
<false/>
<key>FacebookCodelessDebugLogEnabled</key>
<false/>
<key>FacebookSKAdNetworkReportEnabled</key>
<false/>
<key>FBSDKAemAutoSetupEnabled</key>
<false/>
```

Use actual plist **Booleans**, not strings such as `"false"`. Supply a nonblank Meta app
ID and public client token for the approved environment. These are public client
configuration, **not** a CAPI access token. A blank token leaves Meta dormant; remove both
identifiers when the route is not approved. Never bundle CAPI/Marketing tokens.

There is **no supported `FacebookAutoInitEnabled` Info.plist key** in this pinned iOS SDK;
that is an Android startup setting. iOS initialization is explicitly owned by this
adapter. Remove other SDK initialization paths rather than relying on an invented flag.
The extra AEM auto-setup key is read directly by `ApplicationDelegate`; aggregate
attribution/reporting is outside this adapter's scope.

Before initialization the adapter must also set these `Settings.shared` properties to
`false`, regardless of saved values:

| Info.plist key | Runtime property |
| --- | --- |
| `FacebookAutoLogAppEventsEnabled` | `isAutoLogAppEventsEnabled` |
| `FacebookAdvertiserIDCollectionEnabled` | `isAdvertiserIDCollectionEnabled` |
| `FBSDKAutoLogMetaDataEnabled` | `isMetaDataCollectionEnabled` |
| `FBSDKAutoLogProductImprovementEnabled` | `isAutoLogProductImprovementEnabled` |
| `FacebookCodelessDebugLogEnabled` | `isCodelessDebugLogEnabled` |
| `FacebookSKAdNetworkReportEnabled` | `isSKAdNetworkReportEnabled` |

These are adapter-owned settings, not additional app glue. Keep the startup plist flags
even though runtime settings are applied: saved defaults and early vendor startup matter.
`isAdvertiserTrackingEnabled` is **not** an IDFA-collection switch; on supported newer iOS
paths its setter is ignored in favor of ATT status. No ATT prompt is added. ATT and any
consent presentation remain app-owned; denial does not authorize alternative-ID tracking.

Source references at 19.0.1:
[exact keys](https://github.com/facebook/facebook-ios-sdk/blob/v19.0.1/FBSDKCoreKit/FBSDKCoreKit/Settings%2BPersistenceKey.swift),
[defaults/persistence](https://github.com/facebook/facebook-ios-sdk/blob/v19.0.1/FBSDKCoreKit/FBSDKCoreKit/Settings%2BPersistedProperties.swift),
[settings](https://github.com/facebook/facebook-ios-sdk/blob/v19.0.1/FBSDKCoreKit/FBSDKCoreKit/Settings.swift),
[initialization/AEM](https://github.com/facebook/facebook-ios-sdk/blob/v19.0.1/FBSDKCoreKit/FBSDKCoreKit/ApplicationDelegate.swift),
[activation/purchase observers/queues](https://github.com/facebook/facebook-ios-sdk/blob/v19.0.1/FBSDKCoreKit/FBSDKCoreKit/AppEvents/FBSDKAppEvents.m).
There is no separate iOS purchase-auto-logging plist flag in this recipe: the SDK gates
StoreKit purchase observers on `isAutoLogAppEventsEnabled` plus server configuration.
Keep that flag false; never call Meta purchase logging APIs.

## Dashboard recipe: one purchase sender

1. In Meta Events Manager, select the intended app/dataset. Turn **Log In-App Events
   Automatically OFF**. Also disable **Automatically include more app event details**
   and **Automatically send more app information**. Repeat for each environment.
2. Create **separate sandbox and production datasets and CAPI tokens**. Enter each dataset
   ID and token only in the appropriate RevenueCat dashboard Meta integration settings,
   using secure credential handling. Confirm sandbox traffic cannot reach production.
   See the [RevenueCat Meta integration recipe](https://www.revenuecat.com/docs/integrations/attribution/facebook-ads-integration).
3. Set **Trial Started → StartTrial**; **Initial Purchase → Subscribe**;
   **Trial Converted → Subscribe**. Leave **renewal forwarding OFF**. Map nonrenewing
   purchases only if the consumer app actually sells them and that mapping is approved.
4. Enable **send events when ATT not authorized** only after an explicit per-app review
   approving limited-data use, audience policy and disclosures. Without approval leave
   sharing OFF or use the app-owned authorized-only ATT policy; do not assume the local
   adapter gate controls the server setting. No-ATT is not consent or a policy exemption.
5. Audit existing RevenueCat subscriber attributes and integrations for inherited email,
   phone or other marketing PII before enabling sharing. Hashing does not make PII
   anonymous. Remove competing Meta purchase senders/imports; no SDK purchase events,
   custom HTTP sender or parallel attribution provider may duplicate RevenueCat events.

## Pinned capability record and limitations

Source audit: facebook-ios-sdk **19.0.1**, purchases-ios **5.102.0** (resolved from the
allowed 5.x range). This is source evidence, not a completed physical-device privacy or
campaign validation. Re-check for the consumer app's exact lockfile before release.

| Route | Startup and persistence evidence | Disable / transition limit |
| --- | --- | --- |
| RevenueCat AdServices | Device-ID collection is forced OFF at configure. Eligible Apple collection is separately enabled by the RevenueCat adapter; enablement is in-memory, while vendor caches and retries are SDK-owned. | No public AdServices disable counterpart was established. Skipping activation does not cancel work. See the RevenueCat adapter's capability record. |
| Meta SDK | Automatic events, IDFA, metadata and product-improvement defaults are true unless overridden. Settings setters persist to the SDK data store; saved values take precedence over plist values. The actual anonymous ID is persisted to an app-library file. | `stop()` removes CherKit observation/activations only. It does not deinitialize Meta, erase its anonymous ID, recall uploads or cancel its timers, lifecycle observers, persisted events or pending network requests. |
| RevenueCat → Meta CAPI | `setFBAnonymousID` stores a subscriber attribute for the current RevenueCat user. Stored identifiers and server integration configuration outlive the adapter. | Local stop cannot stop server conversions. The SDK accepts nil/empty to delete the attribute, but this adapter does not erase identities; deletion is not cancellation of queued or past events. Required suppression must be established separately or sharing stays OFF. |

In Meta 19.0.1, initialization registers persist-on-close observers, foreground processing
checks saved events, and background processing persists events for later delivery.
Turning off automatic events is **not** a blanket network-off or queue-purge switch.
Metadata opt-out clears cached URLs/screen titles in the audited code, but already
installed swizzles are permanent. Do not treat a flag change as a full privacy reset.
RevenueCat source evidence:
[FB anonymous ID / AdServices](https://github.com/RevenueCat/purchases-ios/blob/5.102.0/Sources/Purchasing/Purchases/Attribution.swift).

Keep restricted/switchable deployments OFF. If an app needs revocation semantics that
cannot be demonstrated for vendor queues and server forwarding, keep the affected route
OFF rather than relying on `stop()`. PostHog consent is independent and authorizes no
advertising collection; this adapter adds no PostHog export, PII, referrer parser or server.

## Physical QA and rollback

Before adoption, save private physical-device evidence using an eligible **release**
configuration and explicit sandbox routing, not debug traffic to production:

- Verify startup flags before init, the actual anonymous ID linked before purchase UI,
  one activation path across foregrounds, and ID refresh for anonymous→login→logout.
- Verify restricted/missing-config launches are Meta-silent while purchases still work.
  Check adult→restricted, offline→online and relaunch; do not infer cancellation from
  missing callbacks. A switchable deployment remains OFF regardless of a happy-path test.
- Check reinstall, existing customer/new device, two devices, and early/offline purchases.
  Confirm only RevenueCat sends StartTrial/Subscribe to the sandbox dataset, renewals do
  not forward, no SDK purchase events appear, and production receives no QA events.
- Check ATT denied/authorized paths against the approved sharing policy and review privacy
  disclosures. Simulator/fake tests prove neither real network behavior nor compliance.

Physical-device delivery, real Meta campaign attribution, dashboard configuration,
revocation/retry transitions and AEM eligibility are **not verified by this source audit**.
Sandbox delivery alone proves none of those campaign outcomes. No per-customer Meta
campaign claim, Google integration or Android campaign-revenue report is provided.

Rollback order: **disable the affected RevenueCat Meta integration first** if server
forwarding must stop. Then remove bundled Meta identifiers/disable the route and replace
this call with `RevenueCatAttribution.start(revenueCat:audience:)` (or plain safe
RevenueCat configuration) through an app release. Use `stop()` to stop new CherKit hooks
in the current process, not as a server kill switch. Leave all safe startup flags OFF;
never restore SDK purchase auto-logging. Purchases and entitlements remain available.
Bundled configuration is not a remote kill switch; past uploads cannot be recalled.
