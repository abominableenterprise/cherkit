tooling: changed

- Contributors only, nothing changes for apps: after `scripts/setup-linux.sh`, a bare `./gradlew <task>` in a fresh
  shell finds the JDK by itself (it sources `${CHERKIT_TOOLS:-~/.cherkit-tools}/env.sh` when `JAVA_HOME` is unset).
  Without that file (macOS, JitPack, IDEs) `./gradlew` behaves as before.
