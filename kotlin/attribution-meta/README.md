# CherKit Attribution Meta — Android

Optional RevenueCat setup plus Meta install/anonymous-ID bridging for a consumer app. **RevenueCat
is the only Meta purchase sender**, through its dashboard-configured Conversions API (CAPI).
**Privacy caveat:** pinned Meta flags do not establish zero advertising-ID reads or suppress all
advanced matching. Keep Meta OFF wherever required suppression cannot be verified; see the audit.

## Install and replace configure

Supply vendor runtimes explicitly; CherKit uses `compileOnly` dependencies. Add JitPack
(`https://jitpack.io`) for CherKit and pin a release:

```kotlin
implementation("com.github.abominableenterprise.cherkit:attribution-meta:v0.9.1")
implementation("com.revenuecat.purchases:purchases:9.29.1")
implementation("com.facebook.android:facebook-core:19.0.1")
```

At the existing `Application.onCreate` configure site, before purchase UI:

```kotlin
import com.abominableenterprise.cherkit.attribution.meta.MetaRevenueCatAttribution
import com.abominableenterprise.cherkit.attribution.revenuecat.Audience
import com.abominableenterprise.cherkit.attribution.revenuecat.RevenueCatSetup

MetaRevenueCatAttribution.start(
    application = this,
    revenueCat = RevenueCatSetup(apiKey = revenueCatPublicKey, appUserId = existingUserId) {
        // Move existing PurchasesConfiguration.Builder options here unchanged.
    },
    audience = Audience.AdultOnly,
)
```

Public API: `MetaRevenueCatAttribution.start(application, revenueCat: RevenueCatSetup, audience: Audience)`
and `MetaRevenueCatAttribution.stop()`. Never also call `Purchases.configure`. Keep native purchase,
login/logout, restore, entitlement and UI calls unchanged. Null `appUserId` retains RC anonymous users.
The RC adapter enforces `automaticDeviceIdentifierCollectionEnabled(false)` after builder options.
Repeated setup preserves the first configuration; an externally configured instance is **not** a
privacy retrofit. Meta stays OFF if the actual configured RC instance allows automatic device-ID
collection. Remove old Meta startup/activation, purchase logging and broad ID-collection glue.

`AdultOnly` means an audited deployment property, not a current role or consent assertion.
Restricted/switchable audiences, debug/test hosts, emulators, missing/blank identifiers or any of the
five required manifest flags not explicitly false enable **purchases only**. Unknown checks fail closed.
Eligible order: safe RC configure → runtime flags OFF → `sdkInitialize(application)` →
`fullyInitialize()` → actual SDK anonymous ID → RC `setFBAnonymousID` → `activateApp`.
One observer refreshes the ID on eligible foregrounds/identity notifications. Never block purchases
on attribution; early purchases can remain unmatched. Kotlin vendor APIs are Android-only; KMP iOS
apps use the Swift adapter. JVM contains vendor-free policy, not an Android SDK integration.

## Manifest/resources — before SDK startup

Use public SDK identifiers in string resources, never a numeric manifest App ID or a CAPI token:

```xml
<resources>
    <string name="facebook_app_id" translatable="false">YOUR_META_APP_ID</string>
    <string name="facebook_client_token" translatable="false">YOUR_META_CLIENT_TOKEN</string>
</resources>
```

The adapter manifest removes `FacebookInitProvider`; retain that removal in every merged variant.
Add the following app entries and inspect the **merged manifest**, resolving conflicts to false:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="com.google.android.gms.permission.AD_ID" tools:node="remove" />
    <uses-permission android:name="android.permission.ACCESS_ADSERVICES_ATTRIBUTION" tools:node="remove" />
    <uses-permission android:name="android.permission.ACCESS_ADSERVICES_AD_ID" tools:node="remove" />
    <uses-permission android:name="android.permission.ACCESS_ADSERVICES_CUSTOM_AUDIENCE" tools:node="remove" />
    <uses-permission android:name="android.permission.ACCESS_ADSERVICES_TOPICS" tools:node="remove" />
    <application>
        <provider android:name="com.facebook.internal.FacebookInitProvider" tools:node="remove" />
        <meta-data android:name="com.facebook.sdk.ApplicationId" android:value="@string/facebook_app_id" />
        <meta-data android:name="com.facebook.sdk.ClientToken" android:value="@string/facebook_client_token" />
        <meta-data android:name="com.facebook.sdk.AutoInitEnabled" android:value="false" />
        <meta-data android:name="com.facebook.sdk.AutoLogAppEventsEnabled" android:value="false" />
        <meta-data android:name="com.facebook.sdk.AdvertiserIDCollectionEnabled" android:value="false" />
        <meta-data android:name="com.facebook.sdk.AutoLogMetaDataEnabled" android:value="false" />
        <meta-data android:name="com.facebook.sdk.AutoLogProductImprovementEnabled" android:value="false" />
        <meta-data android:name="com.facebook.sdk.AutoAppLinkEnabled" android:value="false" />
    </application>
</manifest>
```

The five gate-checked flags are AutoInit, AutoLogAppEvents, AdvertiserIDCollection, AutoLogMetaData
and AutoLogProductImprovement. Runtime `FacebookSdk.set…(false)` counterparts are also applied before
init. Unprefixed `Facebook…` keys are not Android keys. No separate audited Android purchase-logging
manifest key exists: keep AutoLogAppEvents and Meta dashboard **“Log In-App Events Automatically” OFF**.
Disable automatic advanced matching/codeless setup too. Permission removal limits access, not lookup
attempts; review conflicts with other SDKs rather than silently weakening this recipe. No login
activity/callback URI is required for core-only attribution. Never bundle server credentials.

## RevenueCat dashboard recipe

1. In Meta Events Manager, select the intended app/dataset. Turn **Log In-App Events
   Automatically OFF**. Also disable **Automatically include more app event details**
   and **Automatically send more app information**. Explicitly turn **Automatic Advanced
   Matching OFF** and disable codeless setup too. Repeat for each environment. These are
   prerequisites: the server-driven input collector is independent of the metadata flag.
   If suppression cannot be verified, remove the bundled identifiers and keep Meta OFF.
2. Approve per-app audience, limited-data use and disclosures. Audit inherited subscriber PII/marketing
   attributes and other senders before enabling sharing; hashing email/phone does not make them anonymous.
3. Link the Meta app to a production dataset; create **separate sandbox and production datasets and
   CAPI tokens**. Confirm sandbox traffic cannot reach production.
4. Select **Conversions API** in RevenueCat's Meta integration. Enter production **Dataset ID** and
   **Conversions API Token**, plus **Sandbox Dataset ID** and **Sandbox Conversions API Token**.
   Generate tokens securely in Events Manager; enter each dataset ID and token only in the appropriate
   RevenueCat dashboard Meta integration settings. These are not the public SDK client token.
5. Forward **Trial Started → StartTrial**, **Initial Purchase + Trial Converted → Subscribe**.
   **Renewals OFF**, despite the vendor's default Subscribe mapping. Non-renewing purchases → Purchase
   only if sold/approved. If dashboard controls cannot enforce this selection, leave the sender OFF.
6. **“Send events when ATT consent is not authorized” ON only after explicit per-app privacy review**;
   otherwise OFF/authorized-only. This controls App Store events, not Android consent. No ATT prompt
   is added. Keep Meta automatic event logging, advanced matching and codeless setup OFF everywhere.
7. Remove all other Meta purchase senders/imports; no SDK purchase events, custom HTTP sender or
   parallel attribution provider may duplicate RevenueCat events. Test fresh physical-device sandbox purchases: `$fbAnonId`
   before purchase where possible, expected events, no renewals/duplicates/production crossover.
   Play Store CAPI accepts `$fbAnonId` without `$gpsAdId`; broad ID collection is not required.

See [RevenueCat's current dashboard guide](https://www.revenuecat.com/docs/integrations/attribution/meta-ads).
Dashboard behavior is not pinned by the Android version. Delivery does not prove attribution or compliance.

## Pinned capability audit: Facebook 19.0.1 / RevenueCat 9.29.1

Source review used published Maven source JARs and the Facebook AAR manifest, not live-device traffic.
Source links below identify exact tagged files; re-audit before vendor upgrades.

- **Early init / persistence:** [FacebookInitProvider][provider] unconditionally calls `sdkInitialize`;
  AutoInit=false does not prevent it. [FacebookSdk][sdk] starts async features and flushes persisted
  events. `fullyInitialize` only marks readiness, but [GraphResponse][response] rejects responses
  without it. [UserSettingsManager][settings] defaults the five flags to true; pre-init runtime values
  win, otherwise cache precedes manifest. Manifest changes alone do not clear prior state/queues.
- **Actual ID / safe RC assignment:** [AppEventsLoggerImpl][logger] returns the SDK's persisted
  `anonymousAppDeviceGUID` from `com.facebook.sdk.appEventPreferences` (generated `XZ` UUID).
  Use the public `AppEventsLogger.getAnonymousAppDeviceGUID`, not a DIY ID. RC's
  [SubscriberAttributesManager][attributes] collects device IDs during attribution-ID assignment
  unless automatic collection is false. Assignment stores an attribute, not confirmed CAPI delivery.
- **Activation / shutdown:** `activateApp` publishes an install and installs [ActivityLifecycleTracker][lifecycle]
  once. Session events pass through [InternalAppEventsLogger][internal], which suppresses them when
  auto-events is false. Do not promise foreground activation delivery. Install success is persisted.
  No supported public tracker unregister/shutdown exists; `stop()` stops **CherKit's hooks only**.
- **Advertising IDs:** CherKit makes no direct advertising-ID lookup or broad RC collection call.
  Vendor [AttributionIdentifiers][ids] hides its getter when disabled but still calls Google
  reflection/service lookups internally; install publishing reaches it. False flags/removed
  permissions do not prove zero vendor reads. Strict no-read deployments stay OFF.
- **Matching / referrers:** [AppEventsManager][events] can enable [MetadataIndexer][indexer] via server AAM;
  text-field observation/hashed matching data is not gated by AutoLogMetaData. That flag alone is
  not a PII guarantee: require verified dashboard AAM/codeless suppression or stay OFF. Install
  publishing also reaches [InstallReferrerUtil][referrer]. CherKit adds no referrer decoder/export,
  but cannot promise the vendor never collects referrers. Whole-process prohibitions mean OFF.

### Identity observation: pinned internal storage, app listener preserved

RC exposes one `updatedCustomerInfoListener`, not an additive public identity observer;
[CustomerInfoUpdateHandler][info] deduplicates values, so it is not reliable identity notification.
The adapter never replaces it. One retained object handles lifecycle and preference callbacks.
[SharedPreferencesManager][prefs] uses file **`com_revenuecat_purchases_preferences`**, migrating legacy
preferences. [DeviceCache][cache] stores identity under **`com.revenuecat.purchases.<apiKey>.new`**;
[IdentityManager][identity] writes configure/login/logout, sometimes removing before replacement.
The observer uses the **actual configured RC instance's** API key and storage context, preserving
first-config-wins behavior (including a preexisting device-protected context). It ignores transient
removed/blank identities and unrelated attribute writes. Registration precedes the initial refresh;
foregrounds refresh too. This is an **internal storage assumption**, not a supported RC contract.
Notifications can be delayed/coalesced/reentrant; immediate post-login purchases may beat refresh.
Multi-process is unsupported. Public-only foreground reads avoid storage coupling but miss
immediate background identity changes.

## Limits, rollback and verification

Disable the **RC Meta integration first** when server forwarding must stop, then disable/remove the
app route in a release while retaining startup safety. `stop()` does not erase RC IDs, cancel vendor
queues/features or stop CAPI. RC attributes persist/copy across anonymous login; changing audience
cannot undo uploads. Unsafe restricted/switchable/revocable use stays OFF; purchases remain enabled.
Meta campaign results belong in **Ads Manager**, not per-customer RC campaign revenue. No Google
coverage, Android customer campaign revenue, UTM export, custom server or MMP replacement is promised.
Unknown is not proven organic. Test account/reinstall/offline/relaunch transitions privately. Native
host tests cover pre-init flags and observer callbacks, not live Meta traffic, AAM/ID suppression,
real campaign matching or production delivery; those require physical-device/deployment evidence.
Shared policy cases use `fixtures/attribution/meta-gates.json`, separate from the existing RC gates.

[provider]: https://github.com/facebook/facebook-android-sdk/blob/sdk-version-19.0.1/facebook-core/src/main/java/com/facebook/internal/FacebookInitProvider.kt
[sdk]: https://github.com/facebook/facebook-android-sdk/blob/sdk-version-19.0.1/facebook-core/src/main/java/com/facebook/FacebookSdk.kt
[response]: https://github.com/facebook/facebook-android-sdk/blob/sdk-version-19.0.1/facebook-core/src/main/java/com/facebook/GraphResponse.kt
[settings]: https://github.com/facebook/facebook-android-sdk/blob/sdk-version-19.0.1/facebook-core/src/main/java/com/facebook/UserSettingsManager.kt
[logger]: https://github.com/facebook/facebook-android-sdk/blob/sdk-version-19.0.1/facebook-core/src/main/java/com/facebook/appevents/AppEventsLoggerImpl.kt
[lifecycle]: https://github.com/facebook/facebook-android-sdk/blob/sdk-version-19.0.1/facebook-core/src/main/java/com/facebook/appevents/internal/ActivityLifecycleTracker.kt
[internal]: https://github.com/facebook/facebook-android-sdk/blob/sdk-version-19.0.1/facebook-core/src/main/java/com/facebook/appevents/InternalAppEventsLogger.kt
[ids]: https://github.com/facebook/facebook-android-sdk/blob/sdk-version-19.0.1/facebook-core/src/main/java/com/facebook/internal/AttributionIdentifiers.kt
[events]: https://github.com/facebook/facebook-android-sdk/blob/sdk-version-19.0.1/facebook-core/src/main/java/com/facebook/appevents/AppEventsManager.kt
[indexer]: https://github.com/facebook/facebook-android-sdk/blob/sdk-version-19.0.1/facebook-core/src/main/java/com/facebook/appevents/aam/MetadataIndexer.kt
[referrer]: https://github.com/facebook/facebook-android-sdk/blob/sdk-version-19.0.1/facebook-core/src/main/java/com/facebook/internal/InstallReferrerUtil.kt
[attributes]: https://github.com/RevenueCat/purchases-android/blob/9.29.1/purchases/src/main/kotlin/com/revenuecat/purchases/subscriberattributes/SubscriberAttributesManager.kt
[info]: https://github.com/RevenueCat/purchases-android/blob/9.29.1/purchases/src/main/kotlin/com/revenuecat/purchases/CustomerInfoUpdateHandler.kt
[prefs]: https://github.com/RevenueCat/purchases-android/blob/9.29.1/purchases/src/main/kotlin/com/revenuecat/purchases/common/SharedPreferencesManager.kt
[cache]: https://github.com/RevenueCat/purchases-android/blob/9.29.1/purchases/src/main/kotlin/com/revenuecat/purchases/common/caching/DeviceCache.kt
[identity]: https://github.com/RevenueCat/purchases-android/blob/9.29.1/purchases/src/main/kotlin/com/revenuecat/purchases/identity/IdentityManager.kt
