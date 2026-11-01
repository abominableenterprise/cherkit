review: added

- New Android module Review Compose (`:review-compose`, JitPack artifact `review-compose`, brings `review`): Play
  In-App Review (`com.google.android.play:review-ktx` 2.0.2) and Compose effects. `:review` itself still has no
  runtime dependencies, so apps without Play Services can keep using the rules alone.
- `ReviewPromptEffect(coordinator, isIdle)`: on a resumed, idle screen it waits a settle delay (1.5 s by default),
  then launches the Play review when `shouldPrompt()` holds, checking again while the screen stays idle and starting
  over on every `ON_RESUME`.
- `PlayReview.launch(activity, coordinator, requirePlayInstall = true)`: calls `markPrompted()` only after
  `requestReviewFlow()` succeeds, right before `launchReviewFlow()`; a failed request keeps the prompt due. Builds the
  Play Store did not install (sideloaded, emulator installs, other stores) are skipped without consuming anything;
  Play testing tracks install through `com.android.vending` and pass. Play errors never crash; the result says what
  happened (`ReviewLaunchResult`). Play gives no signal when its quota shows nothing, so that version counts as
  prompted.
- `ReviewInterruptionEffect(coordinator, source, visible)` reports your own dialogs, sheets and banners; a source
  that flickers never stays stuck, and every hide restarts the cooldown.
- `ReviewInterruption.WHATS_NEW` (`"whatsNew"`, the same string as Swift's `ReviewInterruption.whatsNew`) in `:review`.
