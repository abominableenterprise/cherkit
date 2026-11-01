review: changed

- Docs: how to base eligibility on state created after the coordinator (e.g. a ViewModel): pass a closure that reads
  it lazily (`isEligible = { eligibilityHolder.value }`) or assign `review.isEligible = { ... }` later (Kotlin and
  Swift). It is read on every check, so keep it cheap and main-thread safe. No behavior change.
