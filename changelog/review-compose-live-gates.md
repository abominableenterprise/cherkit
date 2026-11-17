review-compose: fixed

- Add `ReviewPromptEffect(coordinator, isIdle: () -> Boolean, ...)` to read live model state before and after Play's suspended request. Prefer it when input can make a screen busy before recomposition; the Boolean overload and existing compiled callers remain supported.
- Recheck the composition's live resumed lifecycle and bound Activity before consuming a prompt, so a stopped root cannot launch using another root's eligibility. Finishing or destroyed Activities skip without consuming the version, including direct `PlayReview.launch` calls.
- Expose the existing Play Review dependency as API, so Android consumers can use the public `ReviewManager` injection point and `FakeReviewManager` in tests without adding a separate Play dependency.
