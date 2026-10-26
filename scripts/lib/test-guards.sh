# Guards shared by scripts/test.sh and scripts/test-mac.sh: counting the tests a run executed (so a
# filter that matches nothing fails instead of passing), trimming long logs, and stopping the build
# daemons at the end. Sourced, not run; checked by scripts/tests/test-guards-test.sh. Written for
# macOS's stock bash 3.2.

# tests_run_count <log>: how many tests a `swift test` or `xcodebuild test` log says it executed; 0
# when it ran none (or the log is missing). Reads, taking the largest number found:
#   XCTest summaries          "Executed 12 tests, with 0 failures ..." (also xcodebuild)
#   swift test --parallel     "[3/12] Testing CherKitCoreTests.Foo/testBar"
#   Swift Testing             "Test run with 12 tests in 3 suites passed after ..."
# The largest, not the last: a serial `swift test` ends with Swift Testing's own empty
# "Selected tests ... Executed 0 tests" summary after the XCTest one.
tests_run_count() {
  [ -f "$1" ] || { echo 0; return 0; }
  awk '
    { n = -1 }
    /Executed [0-9]+ tests?,/ { s = $0; sub(/.*Executed /, "", s); sub(/ .*/, "", s); n = s + 0 }
    /^\[[0-9]+\/[0-9]+\] Testing / { s = $0; sub(/^\[[0-9]+\//, "", s); sub(/\].*/, "", s); n = s + 0 }
    /Test run with [0-9]+ tests?/ { s = $0; sub(/.*Test run with /, "", s); sub(/ .*/, "", s); n = s + 0 }
    n > max { max = n }
    END { print max + 0 }
  ' "$1"
}

# gradle_tests_count <module dir> <test task>...: test cases in the JUnit XML reports Gradle wrote for
# those tasks (<module dir>/build/test-results/<task>/TEST-*.xml); 0 when none ran. Gradle deletes a
# task's old reports when it runs again or has no sources, and restores them with a cache hit.
gradle_tests_count() {
  local dir="$1" total=0 task f c
  shift
  for task in "$@"; do
    for f in "$dir/build/test-results/$task"/TEST-*.xml; do
      [ -f "$f" ] || continue
      c="$(grep -c '<testcase' "$f" || true)"
      total=$((total + ${c:-0}))
    done
  done
  echo "$total"
}

# log_excerpt <log> [tail lines]: the `error:` lines of a long build log (deduplicated, at most 30),
# then its last lines (default 60), then the log's path.
log_excerpt() {
  local log="$1" lines="${2:-60}" errors
  errors="$(grep -E 'error:' "$log" 2>/dev/null | awk '!seen[$0]++' | head -n 30 || true)"
  if [ -n "$errors" ]; then
    echo "--- error: lines ---"
    printf '%s\n' "$errors"
  fi
  echo "--- last $lines lines ---"
  tail -n "$lines" "$log"
  echo "--- full log: $log ---"
}

# stop_build_daemons <gradlew>: stops the Gradle daemons (`gradlew --stop`, which stops every daemon of
# this Gradle version, like the one scripts/setup-linux.sh leaves) and this user's Kotlin compile daemons,
# which Gradle starts and does not stop, so a run leaves no java processes behind. The daemons stay up
# during the run, where they make it faster. CHERKIT_KOTLIN_DAEMON_MATCH (default KotlinCompileDaemon) is the
# pkill -f pattern; scripts/tests/test-guards-test.sh sets its own so it never touches real daemons.
stop_build_daemons() {
  "$1" --stop >/dev/null 2>&1 || true
  pkill -u "$(id -u)" -f "${CHERKIT_KOTLIN_DAEMON_MATCH:-KotlinCompileDaemon}" 2>/dev/null || true
  echo "==> stopped the Gradle and Kotlin compile daemons"
}
