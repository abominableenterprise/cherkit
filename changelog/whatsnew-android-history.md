whatsnew: added

- `AndroidWhatsNew.history(context, locales = AndroidWhatsNew.preferredLocales())` returns the same list as
  `AndroidWhatsNew.checker(context).history(locales)`, so a Settings screen needs no checker of its own. Read-only:
  it never appends to the install history or marks notes seen.
