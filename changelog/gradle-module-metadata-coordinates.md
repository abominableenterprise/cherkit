all modules: fixed

- Every project now gets its Maven group and version before any module is configured, and publishing fails if a
  generated Gradle `.module` file names any coordinates other than `com.github.abominableenterprise.cherkit:<module>`
  (component, `available-at` references and capabilities). The `.module` files JitPack serves still show
  `com.github.abominableenterprise:cherkit` as the component because JitPack rewrites that block; Gradle ignores it and
  resolves `sunset`, `whatsnew` and `analytics` together without capability conflicts (see the README's Install
  section).
