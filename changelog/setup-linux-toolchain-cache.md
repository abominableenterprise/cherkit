tooling: changed

- Contributors only, nothing changes for apps: `scripts/setup-linux.sh` now restores Swift and the JDK + Android SDK
  from the VM's task-cache instead of downloading them on every run, and saves them right after a fresh install.
- A failed Gradle warm-up compile no longer fails setup: it prints a warning, skips saving the Gradle cache and exits 0.
