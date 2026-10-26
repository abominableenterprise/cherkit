#!/usr/bin/env bash
# macOS + iOS baseline. Run on a Mac with Xcode:
#   scripts/test-mac.sh            # everything
#   scripts/test-mac.sh sunset     # one module (or analytics, whatsnew), like scripts/test.sh
# Steps: size check (plus the scripts/tests/ tooling tests on full runs), host `swift build` +
# `swift test`, an iOS Simulator build of every library scheme (CherKitUI included), the Swift
# tests on an iPhone simulator, and the Kotlin jvmTest + Android host tests. The simulator tests
# and the Kotlin tests are skipped with a message when no iPhone simulator, JDK 17+ or Android SDK
# is available. Steps run one after another so each gets its own time; logs stay in
# .build/test-mac-logs/.
# Env: DEVELOPER_DIR picks the Xcode; CHERKIT_IOS_DESTINATION overrides the simulator
# (e.g. 'platform=iOS Simulator,name=iPhone 17').
# Written for macOS's stock bash 3.2: no associative arrays, no empty-array expansion under set -u.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
# shellcheck source=lib/test-mac-helpers.sh
. "$ROOT/scripts/lib/test-mac-helpers.sh"

[ "$(uname -s)" = "Darwin" ] || { echo "scripts/test-mac.sh runs on macOS; use scripts/test.sh on Linux" >&2; exit 1; }
command -v xcodebuild >/dev/null || { echo "xcodebuild not found: install Xcode" >&2; exit 1; }

# Folder = registration: modules are the kotlin/ folders with a build file (same as scripts/test.sh).
MODULES=()
for dir in kotlin/*/; do
  [ -f "$dir/build.gradle.kts" ] && MODULES+=("$(basename "$dir")")
done

# kotlin/<module> -> Swift folder name under $2 (e.g. Sources/CherKit* -> CherKitSunset), case-insensitive.
swift_name() {
  local dir
  for dir in $2; do
    [ -d "$dir" ] || continue
    dir="$(basename "$dir")"
    if [ "$(echo "${dir#CherKit}" | tr '[:upper:]' '[:lower:]')" = "$1" ]; then echo "$dir"; return; fi
  done
}

SELECTED=("${MODULES[@]}")
if [ $# -gt 0 ]; then
  for m in "$@"; do
    [ -f "kotlin/$m/build.gradle.kts" ] || { echo "unknown module '$m' (known: ${MODULES[*]})" >&2; exit 2; }
  done
  SELECTED=("$@")
fi

LOGS="$ROOT/.build/test-mac-logs"
DERIVED="$ROOT/.build/xcode"
rm -rf "$LOGS"
mkdir -p "$LOGS"

SUMMARY=()
FAILED=0
record() { SUMMARY+=("$(printf '%-4s %-55s %s' "$1" "$2" "$3")"); }

# run <log-name> <label> <command...>: runs the command with its output in a log; prints the tail on failure.
run() {
  local log="$LOGS/$1.log" label="$2" start=$SECONDS rc=0
  shift 2
  echo "==> $label"
  "$@" >"$log" 2>&1 || rc=$?
  local took=$((SECONDS - start))
  if [ "$rc" -eq 0 ]; then
    record PASS "$label" "${took}s"
  else
    echo "--- $label failed (exit $rc), last lines of $log ---"
    tail -n 40 "$log"
    record FAIL "$label" "${took}s"
    FAILED=1
  fi
}

skip() {
  echo "==> SKIP $1: $2"
  record SKIP "$1" "$2"
}

START=$SECONDS
echo "Xcode: $(xcodebuild -version | tr '\n' ' ')($(xcode-select -p 2>/dev/null || true))"
echo "macOS: $(sw_vers -productVersion) ($(uname -m))"
echo "Modules: ${SELECTED[*]}"

# 1. Size check, as in scripts/test.sh.
if git rev-parse --verify -q origin/main >/dev/null; then
  run size-check "scripts/size-check" scripts/size-check --check-baseline-against origin/main
else
  run size-check "scripts/size-check" scripts/size-check
fi
# The tooling's own tests (size-check, and this script's parsing helpers under macOS's bash), full runs only.
if [ $# -eq 0 ]; then
  for t in scripts/tests/*-test.sh; do run "script-$(basename "$t" .sh)" "$t" "$t"; done
fi

# 2. Host (macOS) swift build + swift test. Unlike Linux, this compiles CherKitUI's SwiftUI code.
SWIFT_TEST=(swift test --parallel)
SWIFT_TARGETS=()
if [ $# -gt 0 ]; then
  for m in "${SELECTED[@]}"; do
    target="$(swift_name "$m" "Sources/CherKit*/")"
    [ -n "$target" ] && SWIFT_TARGETS+=("$target") && SWIFT_TEST+=(--filter "${target}Tests")
  done
fi
run swift-build "swift build (macOS host)" swift build
run swift-test "${SWIFT_TEST[*]} (macOS host)" "${SWIFT_TEST[@]}"

# 3. iOS Simulator build of every library scheme (the package's auto-generated schemes, minus the
#    aggregate CherKit-Package). With modules selected: their schemes, plus CherKitUI when the module
#    has views in Sources/CherKitUI/<Module>.
SCHEMES=()
if ! xcodebuild -list -json >"$LOGS/xcodebuild-list.json" 2>"$LOGS/xcodebuild-list.err"; then
  echo "xcodebuild -list failed:" >&2; cat "$LOGS/xcodebuild-list.err" >&2; exit 1
fi
if ! LISTED="$(python3 -c 'import json,sys; print("\n".join(json.load(sys.stdin)["workspace"]["schemes"]))' \
  <"$LOGS/xcodebuild-list.json")"; then
  echo "could not read the schemes from xcodebuild -list -json (see $LOGS/xcodebuild-list.json)" >&2; exit 1
fi
ALL_SCHEMES="$(echo "$LISTED" | grep -v -- '-Package$' || true)"
has_scheme() { echo "$ALL_SCHEMES" | grep -qx "$1"; }
# Every Sources/<target> folder is a library product with its own scheme; a missing one means discovery broke.
for dir in Sources/*/; do
  has_scheme "$(basename "$dir")" || { echo "no xcodebuild scheme for $(basename "$dir") (schemes: $LISTED)" >&2; exit 1; }
done
if [ $# -eq 0 ]; then
  while IFS= read -r s; do [ -n "$s" ] && SCHEMES+=("$s"); done <<<"$ALL_SCHEMES"
else
  for target in ${SWIFT_TARGETS[@]+"${SWIFT_TARGETS[@]}"}; do has_scheme "$target" && SCHEMES+=("$target"); done
  for m in "${SELECTED[@]}"; do
    if [ -n "$(swift_name "$m" "Sources/CherKitUI/*/")" ] && has_scheme CherKitUI; then
      case " ${SCHEMES[*]-} " in *" CherKitUI "*) ;; *) SCHEMES+=(CherKitUI) ;; esac
    fi
  done
fi
for s in ${SCHEMES[@]+"${SCHEMES[@]}"}; do
  run "ios-build-$s" "xcodebuild build -scheme $s (generic iOS Simulator)" \
    xcodebuild -scheme "$s" -destination 'generic/platform=iOS Simulator' -derivedDataPath "$DERIVED" build
done

# 4. Swift tests on an iPhone simulator: the first iPhone the selected Xcode can run this package on.
DESTINATION="${CHERKIT_IOS_DESTINATION:-}"
SIM_LABEL="$DESTINATION"
if [ -z "$DESTINATION" ]; then
  # Lines look like: { platform:iOS Simulator, arch:arm64, id:<UDID>, OS:27.0, name:iPhone 17 }
  if ! xcodebuild -showdestinations -scheme CherKit-Package >"$LOGS/destinations.log" 2>&1; then
    echo "xcodebuild -showdestinations failed:" >&2; tail -n 20 "$LOGS/destinations.log" >&2; exit 1
  fi
  SIM_LINE="$(grep 'platform:iOS Simulator' "$LOGS/destinations.log" | grep 'name:iPhone' | grep -v 'placeholder' \
    | head -n 1 || true)"
  SIM_ID="$(echo "$SIM_LINE" | sed -n 's/.*id:\([0-9A-Fa-f-]*\),.*/\1/p')"
  if [ -n "$SIM_ID" ]; then
    DESTINATION="platform=iOS Simulator,id=$SIM_ID"
    SIM_LABEL="$(echo "$SIM_LINE" | sed -n 's/.*OS:\([^,]*\), name:\(.*\) }.*/\2, iOS \1/p') ($SIM_ID)"
  fi
fi
if [ -z "$DESTINATION" ]; then
  skip "xcodebuild test (iOS Simulator)" "no iPhone simulator runtime installed for this Xcode"
else
  echo "Simulator: $SIM_LABEL"
  ONLY=()
  for target in ${SWIFT_TARGETS[@]+"${SWIFT_TARGETS[@]}"}; do ONLY+=("-only-testing:${target}Tests"); done
  run ios-test "xcodebuild test -scheme CherKit-Package (iOS Simulator)" \
    xcodebuild -scheme CherKit-Package -destination "$DESTINATION" -derivedDataPath "$DERIVED" \
    test ${ONLY[@]+"${ONLY[@]}"}
fi

# 5. Kotlin: jvmTest needs JDK 17+; the Android host tests also need an Android SDK. Linux covers both.
# Candidates in order: $JAVA_HOME, java on PATH, macOS's registered JDK 17+. The first one that is 17+ wins and
# becomes JAVA_HOME for Gradle; FOUND lists the versions seen, for the skip message.
java_home_of() { "$1" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.home = //p' | head -n 1; }
JAVA_MAJOR=""
FOUND=""
for candidate in "${JAVA_HOME:+$JAVA_HOME/bin/java}" "$(command -v java || true)" \
  "$(/usr/libexec/java_home -v 17+ 2>/dev/null | sed 's|$|/bin/java|' || true)"; do
  [ -n "$candidate" ] && [ -x "$candidate" ] || continue
  major="$(java_major "$candidate" || true)"
  [ -n "$major" ] || continue
  FOUND="${FOUND:+$FOUND, }$major"
  if [ "$major" -ge 17 ] && JH="$(java_home_of "$candidate")" && [ -n "$JH" ]; then
    export JAVA_HOME="$JH"; JAVA_MAJOR="$major"; break
  fi
done

# java_major, pick_android_sdk and use_android_sdk come from scripts/lib/test-mac-helpers.sh.
SDK="$(pick_android_sdk local.properties "$HOME/Library/Android/sdk")"

if [ -z "$JAVA_MAJOR" ]; then
  skip "./gradlew jvmTest + testAndroidHostTest" "no JDK 17+ found (found: ${FOUND:-none}); Linux covers Kotlin"
else
  GRADLE_TASKS=()
  for m in "${SELECTED[@]}"; do
    GRADLE_TASKS+=(":$m:jvmTest")
    [ -n "$SDK" ] && GRADLE_TASKS+=(":$m:testAndroidHostTest")
  done
  if [ -n "$SDK" ]; then use_android_sdk "$SDK"; fi
  run gradle "./gradlew ${GRADLE_TASKS[*]} (JDK $JAVA_MAJOR)" ./gradlew --console=plain "${GRADLE_TASKS[@]}"
  [ -n "$SDK" ] || skip "./gradlew testAndroidHostTest" "no Android SDK (ANDROID_HOME); Linux covers it"
fi

echo
echo "== test-mac summary ($((SECONDS - START))s total, logs in ${LOGS#"$ROOT"/}) =="
printf '%s\n' "${SUMMARY[@]}"
if [ "$FAILED" -ne 0 ]; then
  echo "FAILED for: ${SELECTED[*]} ($((SECONDS - START))s)"
  exit 1
fi
echo "All passed for: ${SELECTED[*]} ($((SECONDS - START))s)"
