#!/usr/bin/env bash
# Run everything that runs on Linux:  scripts/test.sh
# Run one module on both platforms:   scripts/test.sh sunset   (or analytics, whatsnew)
# Run setup first: scripts/setup-linux.sh. Swift and Gradle run in parallel; their logs
# are printed when each finishes.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="${CHERKIT_TOOLS:-$HOME/.cherkit-tools}"
# A relative CHERKIT_TOOLS is relative to the caller's directory, as in setup-linux.sh.
case "$TOOLS" in /*) ;; *) TOOLS="$PWD/$TOOLS" ;; esac
cd "$ROOT"
# shellcheck disable=SC1091
[ -f "$TOOLS/env.sh" ] && . "$TOOLS/env.sh"

# Folder = registration: modules are the kotlin/ folders with a build file.
MODULES=()
for dir in kotlin/*/; do
  [ -f "$dir/build.gradle.kts" ] && MODULES+=("$(basename "$dir")")
done

# kotlin/<module> -> Swift test target, from the Sources/CherKit<Module> folder name.
swift_test_target() {
  local dir
  for dir in Sources/CherKit*/; do
    dir="$(basename "$dir")"
    if [ "$(echo "${dir#CherKit}" | tr '[:upper:]' '[:lower:]')" = "$1" ]; then echo "${dir}Tests"; return; fi
  done
}

SELECTED=("${MODULES[@]}")
if [ $# -gt 0 ]; then
  for m in "$@"; do
    [ -f "kotlin/$m/build.gradle.kts" ] || { echo "unknown module '$m' (known: ${MODULES[*]})" >&2; exit 2; }
  done
  SELECTED=("$@")
fi

command -v swift >/dev/null || { echo "swift not found: run scripts/setup-linux.sh" >&2; exit 1; }
[ -n "${JAVA_HOME:-}" ] || command -v java >/dev/null || { echo "java not found: run scripts/setup-linux.sh" >&2; exit 1; }

START=$SECONDS
echo "==> size-check"
if git rev-parse --verify -q origin/main >/dev/null; then
  scripts/size-check --check-baseline-against origin/main
else
  scripts/size-check
fi
# The tooling's own tests (fast; size-check and the test-mac.sh parsing helpers), on full runs only.
if [ $# -eq 0 ]; then
  for t in scripts/tests/*-test.sh; do
    echo "==> $t"
    out="$("$t" 2>&1)" || { echo "$out"; exit 1; }
    echo "$out" | tail -n 1
  done
fi

SWIFT_ARGS=(test --parallel)
GRADLE_TASKS=()
for m in "${SELECTED[@]}"; do
  if [ $# -gt 0 ]; then
    target="$(swift_test_target "$m")"
    [ -n "$target" ] && SWIFT_ARGS+=(--filter "$target")
  fi
  GRADLE_TASKS+=(":$m:jvmTest" ":$m:testAndroidHostTest")
done

LOGS="$(mktemp -d)"
trap 'rm -rf "$LOGS"' EXIT
echo "==> swift ${SWIFT_ARGS[*]}"
echo "==> ./gradlew ${GRADLE_TASKS[*]}"
(swift "${SWIFT_ARGS[@]}" >"$LOGS/swift.log" 2>&1; echo $? >"$LOGS/swift.rc") &
SWIFT_PID=$!
(./gradlew --quiet --console=plain "${GRADLE_TASKS[@]}" >"$LOGS/gradle.log" 2>&1; echo $? >"$LOGS/gradle.rc") &
GRADLE_PID=$!

FAILED=()
wait "$SWIFT_PID" || true
echo "--- swift ---"; tail -n 40 "$LOGS/swift.log"
[ "$(cat "$LOGS/swift.rc")" = "0" ] || FAILED+=(swift)
wait "$GRADLE_PID" || true
echo "--- gradle ---"; tail -n 60 "$LOGS/gradle.log"
[ "$(cat "$LOGS/gradle.rc")" = "0" ] || FAILED+=(gradle)

if [ ${#FAILED[@]} -gt 0 ]; then
  echo "FAILED: ${FAILED[*]} ($((SECONDS - START))s)"
  exit 1
fi
echo "All passed for: ${SELECTED[*]} ($((SECONDS - START))s)"
