#!/usr/bin/env bash
# Checks scripts/lib/test-guards.sh (zero-test guard, log trimming, daemon cleanup) with sample logs,
# then runs scripts/test.sh end to end against a fake swift and gradlew in a throwaway copy, so a
# filter that matches nothing must fail and the daemons must be stopped even on failure. Runs on Linux
# and macOS. Run: scripts/tests/test-guards-test.sh
set -euo pipefail

SCRIPTS="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=../lib/test-guards.sh
. "$SCRIPTS/lib/test-guards.sh"
WORK="$(mktemp -d)"
DAEMONS=()
cleanup() {
  for pid in ${DAEMONS[@]+"${DAEMONS[@]}"}; do kill "$pid" 2>/dev/null || true; done
  rm -rf "$WORK"
}
trap cleanup EXIT
FAILS=0
N=0

check() { # check <label> <want> <got>
  N=$((N + 1))
  if [ "$2" = "$3" ]; then echo "ok   $1"; else echo "FAIL $1: want [$2], got [$3]"; FAILS=$((FAILS + 1)); fi
}

count() { printf '%s\n' "$@" >"$WORK/log"; tests_run_count "$WORK/log"; }

# --- tests_run_count, on lines copied from real runs.
check "swift test --parallel, filter matched nothing" 0 \
  "$(count 'Build complete! (1.55 secs)' 'warning: No matching test cases were run')"
check "swift test --parallel progress lines" 3 \
  "$(count '[1/3] Testing CherKitCoreTests.AppVersionFixtureTests/testParseCases' \
    '[2/3] Testing CherKitCoreTests.AppVersionFixtureTests/testDescriptionJoinsComponents' \
    '[3/3] Testing CherKitCoreTests.AppVersionFixtureTests/testCompareCases')"
check "serial swift test: XCTest count, then Swift Testing's empty suite" 3 \
  "$(count "Test Suite 'Selected tests' passed at 2026-10-26 11:16:56.799" \
    '	 Executed 3 tests, with 0 failures (0 unexpected) in 0.003 (0.003) seconds' \
    "Test Suite 'Selected tests' passed at 2026-10-26 11:16:56.810" \
    '	 Executed 0 tests, with 0 failures (0 unexpected) in 0.0 (0.0) seconds')"
check "serial swift test, filter matched nothing" 0 \
  "$(count '	 Executed 0 tests, with 0 failures (0 unexpected) in 0.0 (0.0) seconds')"
check "xcodebuild test summary" 141 \
  "$(count "Test Suite 'All tests' passed at 2026-10-26 10:00:00.000." \
    '	 Executed 141 tests, with 0 failures (0 unexpected) in 2.345 (2.501) seconds' '** TEST SUCCEEDED **')"
check "xcodebuild test, -only-testing matched nothing" 0 \
  "$(count '	 Executed 0 tests, with 0 failures (0 unexpected) in 0.000 (0.001) seconds' '** TEST SUCCEEDED **')"
check "single test" 1 "$(count '	 Executed 1 test, with 0 failures (0 unexpected) in 0.001 (0.001) seconds')"
check "Swift Testing summary" 12 "$(count '✔ Test run with 12 tests in 3 suites passed after 0.004 seconds.')"
check "Swift Testing, no tests" 0 "$(count '✔ Test run with 0 tests in 0 suites passed after 0.001 seconds.')"
check "missing log" 0 "$(tests_run_count "$WORK/no-such.log")"

# --- gradle_tests_count: JUnit XML reports per task.
mkdir -p "$WORK/mod/build/test-results/jvmTest" "$WORK/mod/build/test-results/testAndroidHostTest" \
  "$WORK/mod/build/test-results/otherTest" "$WORK/empty"
printf '<testsuite>\n  <testcase name="a"/>\n  <testcase name="b"></testcase>\n</testsuite>\n' \
  >"$WORK/mod/build/test-results/jvmTest/TEST-com.example.ATest.xml"
printf '<testsuite>\n  <testcase name="c"/>\n</testsuite>\n' \
  >"$WORK/mod/build/test-results/testAndroidHostTest/TEST-com.example.BTest.xml"
printf '<testsuite>\n  <testcase name="z"/>\n</testsuite>\n' >"$WORK/mod/build/test-results/otherTest/TEST-Z.xml"
check "reports of the named tasks are summed" 3 "$(gradle_tests_count "$WORK/mod" jvmTest testAndroidHostTest)"
check "other tasks' reports are ignored" 2 "$(gradle_tests_count "$WORK/mod" jvmTest)"
check "no reports" 0 "$(gradle_tests_count "$WORK/empty" jvmTest testAndroidHostTest)"

# --- log_excerpt: error: lines (once each), then the tail, then the path.
# 102 lines: the same error twice, 1..99, BUILD FAILED. The last 60 lines start at 41.
{ echo '/x/A.swift:3:1: error: cannot find Foo in scope'; echo '/x/A.swift:3:1: error: cannot find Foo in scope'
  seq 1 99; echo '** BUILD FAILED **'; } >"$WORK/xc.log"
out="$(log_excerpt "$WORK/xc.log" 60)"
check "error: line printed once, before the tail" "1|2" \
  "$(printf '%s\n' "$out" | grep -c 'error: cannot find Foo')|$(printf '%s\n' "$out" | grep -n 'error: cannot find Foo' | cut -d: -f1)"
check "tail starts 60 lines from the end" "41" "$(printf '%s\n' "$out" | sed -n '/^--- last 60 lines ---$/{n;p;}')"
check "tail ends with the log's last line" "** BUILD FAILED **" "$(printf '%s\n' "$out" | tail -n 2 | head -n 1)"
check "early lines are trimmed" "" "$(printf '%s\n' "$out" | grep -x '40' || true)"
check "ends with the log path" "--- full log: $WORK/xc.log ---" "$(printf '%s\n' "$out" | tail -n 1)"
check "no error: section without errors" "" "$(seq 1 5 >"$WORK/ok.log"; log_excerpt "$WORK/ok.log" | grep 'error: lines' || true)"

# --- scripts/test.sh end to end, with fake tools. FAKE_SWIFT_TESTS / FAKE_GRADLE_TESTS set how many tests
# the fakes "run"; the fake gradlew records --stop and starts a fake Kotlin compile daemon, as a real build does.
# The fake daemon has a name unique to this run, and test.sh is told to pkill only that name, never real daemons.
FAKE_DAEMON="FakeKotlinCompileDaemon-$$-$RANDOM"
repo="$WORK/repo"
mkdir -p "$repo/scripts/lib" "$repo/kotlin/demo" "$repo/Sources/CherKitDemo" "$WORK/bin" "$WORK/tools"
cp "$SCRIPTS/test.sh" "$repo/scripts/test.sh"
cp "$SCRIPTS/lib/test-guards.sh" "$repo/scripts/lib/test-guards.sh"
touch "$repo/kotlin/demo/build.gradle.kts"
printf '#!/bin/sh\necho "size-check: ok"\n' >"$repo/scripts/size-check"
cat >"$WORK/bin/swift" <<'SH'
#!/bin/sh
echo "Build complete!"
if [ "$FAKE_SWIFT_TESTS" -eq 0 ]; then echo "warning: No matching test cases were run"; exit 0; fi
i=1
while [ "$i" -le "$FAKE_SWIFT_TESTS" ]; do echo "[$i/$FAKE_SWIFT_TESTS] Testing CherKitDemoTests.T/test$i"; i=$((i + 1)); done
SH
cat >"$repo/gradlew" <<'SH'
#!/usr/bin/env bash
if [ "$1" = "--stop" ]; then echo stopped >>"$FAKE_STOP_LOG"; exit 0; fi
# A fake Kotlin compile daemon that outlives the build, like the real one.
(exec -a "$FAKE_DAEMON" sleep 300) >/dev/null 2>&1 &
echo "$!" >"$FAKE_DAEMON_PID"
rm -rf kotlin/demo/build/test-results
mkdir -p kotlin/demo/build/test-results/jvmTest
i=0
{ echo '<testsuite>'; while [ "$i" -lt "$FAKE_GRADLE_TESTS" ]; do echo "<testcase name=\"t$i\"/>"; i=$((i + 1)); done
  echo '</testsuite>'; } >kotlin/demo/build/test-results/jvmTest/TEST-Demo.xml
exit "${FAKE_GRADLE_RC:-0}"
SH
chmod +x "$repo/scripts/test.sh" "$repo/scripts/size-check" "$WORK/bin/swift" "$repo/gradlew"

# running <pid>: alive or gone (a killed process left as a zombie by a non-reaping init counts as gone).
running() {
  case "$(ps -o stat= -p "$1" 2>/dev/null)" in "" | Z*) echo gone ;; *) echo alive ;; esac
}

# e2e <label> <want exit> <want output pattern> <swift tests> <gradle tests> [gradle rc]
e2e() {
  local rc=0 out
  : >"$WORK/stop.log"
  out="$(cd "$repo" && PATH="$WORK/bin:$PATH" CHERKIT_TOOLS="$WORK/tools" JAVA_HOME=/fake-jdk \
    FAKE_SWIFT_TESTS="$4" FAKE_GRADLE_TESTS="$5" FAKE_GRADLE_RC="${6:-0}" FAKE_STOP_LOG="$WORK/stop.log" \
    FAKE_DAEMON_PID="$WORK/daemon.pid" FAKE_DAEMON="$FAKE_DAEMON" CHERKIT_KOTLIN_DAEMON_MATCH="$FAKE_DAEMON" \
    scripts/test.sh demo 2>&1)" || rc=$?
  local pid
  pid="$(cat "$WORK/daemon.pid")"
  DAEMONS+=("$pid")
  N=$((N + 1))
  if [ "$rc" -eq "$2" ] && printf '%s' "$out" | grep -q -- "$3"; then
    echo "ok   $1"
  else
    echo "FAIL $1: exit $rc (want $2), output:"; printf '%s\n' "$out" | sed 's/^/     /'; FAILS=$((FAILS + 1))
  fi
  check "$1: gradlew --stop ran" stopped "$(cat "$WORK/stop.log")"
  sleep 0.2 # let the killed daemon exit
  check "$1: the Kotlin daemon the run started is gone" gone "$(running "$pid")"
}

e2e "module with tests passes" 0 "All passed for: demo" 3 2
e2e "swift filter matching nothing fails" 1 "swift test --parallel --filter CherKitDemoTests executed 0 tests" 0 2
e2e "Gradle module with no tests fails" 1 "Gradle ran 0 tests for :demo" 3 0
e2e "failing Gradle still stops the daemons" 1 "FAILED: gradle" 3 2 1

echo "test-guards tests: $((N - FAILS))/$N passed"
[ "$FAILS" -eq 0 ]
