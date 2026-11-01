sunset: changed

- CherKitUI: `.sunsetPrompt(checker:)` shows at most one soft prompt per session. Once the user answers a soft prompt
  ("Update" or "Not now"), re-checks on later returns from the background show no other soft prompt until the app is
  launched again. A hard block still shows. Before, an "Update" tap on a release prompt that was not followed by an
  update could show the prompt again on the next re-check in the same process.
