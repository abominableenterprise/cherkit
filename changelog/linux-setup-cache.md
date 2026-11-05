tooling: changed

- Linux setup reuses downloaded Gradle dependencies across release and module edits, skips warm-up compilation when both caches are available, and exits quickly on an already configured VM. Set `CHERKIT_GRADLE_WARMUP=1` to force compilation.
