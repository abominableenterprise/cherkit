whatsnew: fixed

- Docs: the Android setup in `kotlin/whatsnew/README.md` no longer says to add the repo's `whatsnew/` folder as an
  assets `srcDir`. That put the files at the root of `assets/`, so `AndroidWhatsNew.checker(context)` found no notes.
  It now shows a Gradle `Sync` task into `build/generated/whatsnew-assets/whatsnew/`, with the parent folder added as
  a plain-path assets `srcDir` and `preBuild` depending on the sync (works on AGP 9). If your app followed the old
  advice, switch to this setup or copy the files to `src/main/assets/whatsnew/`.
- The `WhatsNewStore` docs now point to the shipped stores (`UserDefaultsWhatsNewStore`, `KeyValueWhatsNewStore`,
  `AndroidWhatsNew.store(context)`) instead of saying they come later.
