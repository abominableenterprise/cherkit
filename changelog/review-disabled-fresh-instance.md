review: changed

- Swift: `ReviewCoordinator.disabled` now returns a fresh, independent disabled coordinator on each access instead of
  one shared instance, so setting `isEligible` (or any other state) on it no longer leaks to other callers. The call
  site is unchanged (`isUITest ? .disabled : ReviewCoordinator()`); keep the coordinator you got for the app's lifetime
  (a `let` or `@State`) rather than reading `.disabled` again, and don't compare coordinators with `=== .disabled`
  (check `isDisabled` instead). This matches Kotlin's `ReviewCoordinator.disabled()`.
