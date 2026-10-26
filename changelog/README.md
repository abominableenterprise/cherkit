# Changelog fragments

Developer-facing notes on the CherKit library for the apps that use it.

- Every PR adds one file here, `changelog/<slug>.md`, where `<slug>` is short and unique (e.g. `sunset-hard-gate.md`).
  This avoids merge conflicts: never edit a central CHANGELOG.
- A PR may skip the fragment only when it is internal and apps cannot notice it (docs, tests, tooling).
- Fragments are compiled into the release notes for each version at release time, then deleted.

Format: a first line naming the module and the change kind, then one or more bullet points written for app developers.

```md
sunset: added

- `SunsetChecker` reads the hosted min-version config and reports `.ok`, `.softUpdate` or `.hardUpdate`.
```

Kinds: `added`, `changed`, `fixed`, `removed`, `breaking`. A `breaking` fragment says what breaks and how to
migrate, and the release that ships it bumps the major version.
