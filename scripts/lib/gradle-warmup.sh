# gradle_warmup, used by scripts/setup-linux.sh. Sourced, not run; checked by scripts/tests/gradle-warmup-test.sh.

# gradle_warmup <root>: resolves every dependency and compiles once in <root>, so later test runs only build
# CherKit's own code. Never fatal: when Gradle fails (e.g. "Gradle build daemon disappeared unexpectedly") it
# prints a warning, stops the daemon and returns 1, so the caller can skip saving a half-filled Gradle cache
# and still finish setup. Test runs fail loudly on their own if Gradle is really broken.
gradle_warmup() {
  if (cd "$1" && ./gradlew --quiet jvmTestClasses compileAndroidHostTest); then
    return 0
  fi
  echo "warning: gradle warm-up failed (continuing): tests will resolve dependencies themselves" >&2
  (cd "$1" && ./gradlew --quiet --stop) >/dev/null 2>&1 || true
  return 1
}
