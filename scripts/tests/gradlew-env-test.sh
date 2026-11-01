#!/usr/bin/env bash
# Checks the CherKit hook in ./gradlew that sources ${CHERKIT_TOOLS:-$HOME/.cherkit-tools}/env.sh when JAVA_HOME is
# unset (`gradle wrapper` regenerates gradlew without it). Runs `gradlew --version` from a throwaway copy of the
# wrapper, with JAVA_HOME unset and no java on PATH, against a fake env.sh that points at the real JDK. Without a JDK
# only the presence check runs. Run: scripts/tests/gradlew-env-test.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
FAILS=0
N=0

check() { # check <label> <want> <got>
  N=$((N + 1))
  if [ "$2" = "$3" ]; then echo "ok   $1"; else echo "FAIL $1: want [$2], got [$3]"; FAILS=$((FAILS + 1)); fi
}

check "gradlew has the env.sh hook" yes \
  "$(grep -q 'CHERKIT_ENV=${CHERKIT_TOOLS:-$HOME/.cherkit-tools}/env.sh' "$ROOT/gradlew" && echo yes || echo no)"

# The real JDK: JAVA_HOME (scripts/test.sh has sourced env.sh), else the java on PATH, else the setup's env.sh.
JDK="${JAVA_HOME:-}"
if [ -z "$JDK" ] && command -v java >/dev/null; then
  # `|| true`: macOS's /usr/bin/java stub exits nonzero without a JDK; that must reach the skip below, not abort.
  JDK="$({ java -XshowSettings:properties -version 2>&1 || true; } | sed -n 's/^ *java\.home = //p' | head -n 1)"
fi
TOOLS_ENV="${CHERKIT_TOOLS:-$HOME/.cherkit-tools}/env.sh"
if [ -z "$JDK" ] && [ -f "$TOOLS_ENV" ]; then
  # shellcheck disable=SC1090
  JDK="$(. "$TOOLS_ENV" && echo "${JAVA_HOME:-}")"
fi
if [ -z "$JDK" ] || [ ! -x "$JDK/bin/java" ]; then
  echo "test-gradlew-env: no JDK found, behaviour checks skipped"
  echo "gradlew-env tests: $((N - FAILS))/$N passed"
  [ "$FAILS" -eq 0 ]
  exit
fi

# A copy of the wrapper, so nothing here touches the checkout.
mkdir -p "$WORK/repo/gradle" "$WORK/tools" "$WORK/caller"
cp "$ROOT/gradlew" "$WORK/repo/gradlew"
cp -R "$ROOT/gradle/wrapper" "$WORK/repo/gradle/wrapper"
# env.sh exports the real JDK and records that it was sourced.
printf 'export JAVA_HOME=%q\ntouch %q\n' "$JDK" "$WORK/sourced" >"$WORK/tools/env.sh"

# An isolated PATH with only the tools gradlew and the fake env.sh use, and no java (system dirs like macOS's
# /usr/bin hold a java stub next to these tools, so they can't just be dropped from PATH).
mkdir -p "$WORK/bin"
for t in uname xargs sed tr touch ls; do ln -s "$(command -v "$t")" "$WORK/bin/$t"; done
NOJAVA_PATH="$WORK/bin"

# gradlew_version <dir to run from> <JAVA_HOME or empty> <CHERKIT_TOOLS>: runs `gradlew --version` there and sets
# RC (its exit code), SOURCED (sourced / not-sourced: whether env.sh ran) and OUT (its output).
gradlew_version() {
  RC=0
  rm -f "$WORK/sourced"
  OUT="$(cd "$1" && env -u JAVA_HOME ${2:+JAVA_HOME="$2"} PATH="$NOJAVA_PATH" CHERKIT_TOOLS="$3" \
    "$WORK/repo/gradlew" --version 2>&1)" || RC=$?
  SOURCED="$([ -f "$WORK/sourced" ] && echo sourced || echo not-sourced)"
}

gradlew_version "$WORK/repo" "" "$WORK/tools"
check "JAVA_HOME unset: env.sh is sourced and gradlew runs" "0 sourced" "$RC $SOURCED"
check "JAVA_HOME unset: Gradle starts on the env.sh JDK" yes \
  "$(grep -q '^Gradle ' <<<"$OUT" && grep -qF "$JDK" <<<"$OUT" && echo yes || echo no)"

gradlew_version "$WORK" "" tools
check "relative CHERKIT_TOOLS is relative to the caller's directory" "0 sourced" "$RC $SOURCED"

gradlew_version "$WORK/caller" "$JDK" "$WORK/tools"
check "JAVA_HOME set: env.sh is not sourced" "0 not-sourced" "$RC $SOURCED"

gradlew_version "$WORK/caller" "" "$WORK/no-tools"
check "no env.sh: no-op, gradlew's usual JAVA_HOME error" "1 not-sourced 1" \
  "$RC $SOURCED $(grep -c 'JAVA_HOME is not set' <<<"$OUT")"

echo "gradlew-env tests: $((N - FAILS))/$N passed"
[ "$FAILS" -eq 0 ]
