#!/usr/bin/env bash
# Checks gradle_warmup (scripts/lib/gradle-warmup.sh, used by scripts/setup-linux.sh) against a stub gradlew in a
# throwaway directory: a failed warm-up must warn, stop the daemon and report failure without aborting a
# `set -e` caller, and a good one must succeed quietly. Run: scripts/tests/gradle-warmup-test.sh
set -euo pipefail

SCRIPTS="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=../lib/gradle-warmup.sh
. "$SCRIPTS/lib/gradle-warmup.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
FAILS=0
N=0

check() { # check <label> <want> <got>
  N=$((N + 1))
  if [ "$2" = "$3" ]; then echo "ok   $1"; else echo "FAIL $1: want [$2], got [$3]"; FAILS=$((FAILS + 1)); fi
}

# stub <exit code for builds>: a gradlew that logs its arguments and fails builds (not --stop) with that code.
stub() {
  cat >"$WORK/gradlew" <<EOF
#!/usr/bin/env bash
echo "\$*" >>"$WORK/calls"
case " \$* " in *" --stop "*) exit 0 ;; esac
[ $1 -eq 0 ] || echo "Gradle build daemon disappeared unexpectedly" >&2
exit $1
EOF
  chmod +x "$WORK/gradlew"
  rm -f "$WORK/calls"
}

# The same pattern as setup-linux.sh, under set -e in a child shell: the caller must keep going.
run_like_setup() {
  bash -c 'set -euo pipefail; . "$1"; saved=yes; if ! gradle_warmup "$2"; then saved=no; fi; echo "saved=$saved"' \
    _ "$SCRIPTS/lib/gradle-warmup.sh" "$WORK" 2>"$WORK/err"
}

stub 1
out="$(run_like_setup)" && rc=0 || rc=$?
check "failed warm-up: setup keeps going (exit 0)" 0 "$rc"
check "failed warm-up: Gradle caches are not saved" saved=no "$out"
check "failed warm-up: prints the warning" yes \
  "$(grep -q 'warning: gradle warm-up failed (continuing): tests will resolve dependencies themselves' "$WORK/err" \
    && echo yes || echo no)"
check "failed warm-up: stops the daemon" yes "$(grep -q -- '--stop' "$WORK/calls" && echo yes || echo no)"

stub 0
out="$(run_like_setup)" && rc=0 || rc=$?
check "good warm-up: exit 0" 0 "$rc"
check "good warm-up: Gradle caches are saved" saved=yes "$out"
check "good warm-up: runs the compile tasks" "--quiet jvmTestClasses compileAndroidHostTest" "$(head -n 1 "$WORK/calls")"
check "good warm-up: no warning" "" "$(cat "$WORK/err")"

echo "gradle-warmup tests: $((N - FAILS))/$N passed"
[ "$FAILS" -eq 0 ]
