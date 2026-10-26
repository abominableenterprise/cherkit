sunset: fixed

- CherKitUI: `.sunsetPrompt(checker:)` no longer misses a hard block until the next cold start when the launch check
  fails (fetch dropped or timed out, or a body that does not parse, with no usable cached config, e.g. on a fresh
  install). A failed check no longer counts toward `minimumInterval`; it is retried once about 3 seconds later, and if
  that fails too, the next return from the background re-checks whatever the interval. The public `SunsetChecker`
  API is unchanged.
