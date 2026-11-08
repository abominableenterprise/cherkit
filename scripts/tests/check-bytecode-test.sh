#!/usr/bin/env bash
# Synthetic headers only: no javac, Gradle, or downloaded dependencies needed.
# Run: scripts/tests/check-bytecode-test.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
FAILS=0
N=0

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/jar" ]; then
  JAR="$JAVA_HOME/bin/jar"
elif command -v jar >/dev/null; then
  JAR="$(command -v jar)"
elif command -v zip >/dev/null; then
  JAR=""
else
  echo "ERROR: tests require jar (JDK) or zip" >&2
  exit 1
fi

archive() { # archive <output> <directory>
  if [ -n "$JAR" ]; then
    "$JAR" cf "$1" -C "$2" .
  else
    (cd "$2" && zip -qr "$1" .)
  fi
}

check() { # check <label> <want> <got>
  N=$((N + 1))
  if [ "$2" = "$3" ]; then
    echo "ok   $1"
  else
    echo "FAIL $1: want [$2], got [$3]"
    FAILS=$((FAILS + 1))
  fi
}

run_guard() {
  RC=0
  OUT="$(bash "$ROOT/scripts/check-bytecode.sh" "$@" 2>&1)" || RC=$?
}

contains() { # contains <label> <literal output>
  check "$1" yes "$(grep -qF -- "$2" <<<"$OUT" && echo yes || echo no)"
}

mkdir -p "$WORK/java17/pkg" "$WORK/java21/pkg" "$WORK/mixed/pkg" "$WORK/aar" "$WORK/none"
printf '\312\376\272\276\000\000\000\075' >"$WORK/java17/pkg/Compatible.class"
printf '\312\376\272\276\000\000\000\101' >"$WORK/java21/pkg/TooNew.class"
cp "$WORK/java17/pkg/Compatible.class" "$WORK/mixed/pkg/"
cp "$WORK/java21/pkg/TooNew.class" "$WORK/mixed/pkg/"
printf 'not a class\n' >"$WORK/none/README"
archive "$WORK/java17.jar" "$WORK/java17"
archive "$WORK/java21.jar" "$WORK/java21"
archive "$WORK/mixed.jar" "$WORK/mixed"
archive "$WORK/no-classes.jar" "$WORK/none"

run_guard "$WORK/java17.jar"
check "Java 17 JAR passes" 0 "$RC"
contains "JAR reports maximum" "$WORK/java17.jar: maximum class major version 61"

run_guard "$WORK/java21.jar"
check "Java 21 JAR fails" 1 "$RC"
contains "failure names class and version" 'pkg/TooNew.class: class major version 65 exceeds 61'
contains "failing archive reports maximum" 'maximum class major version 65'

run_guard "$WORK/mixed.jar"
check "mixed versions fail" 1 "$RC"
contains "maximum covers all classes" 'maximum class major version 65'

cp "$WORK/java17.jar" "$WORK/aar/classes.jar"
archive "$WORK/java17.aar" "$WORK/aar"
cp "$WORK/java21.jar" "$WORK/aar/classes.jar"
archive "$WORK/java21.aar" "$WORK/aar"
run_guard "$WORK/java17.aar"
check "Java 17 AAR passes" 0 "$RC"
contains "AAR reports nested maximum" "$WORK/java17.aar!classes.jar: maximum class major version 61"
run_guard "$WORK/java21.aar"
check "Java 21 AAR fails" 1 "$RC"
contains "AAR failure identifies nested class" 'java21.aar!classes.jar!pkg/TooNew.class: class major version 65'

run_guard "$WORK/java21.jar" "$WORK/java17.aar"
check "multiple archives retain failure" 1 "$RC"
contains "later archives still checked" "$WORK/java17.aar!classes.jar: maximum class major version 61"

run_guard
check "no arguments is usage error" 2 "$RC"
contains "usage is printed" 'Usage:'
run_guard "$WORK/missing.jar"
check "missing archive fails" 1 "$RC"
contains "missing archive diagnostic" 'missing or empty archive'
: >"$WORK/empty.jar"
run_guard "$WORK/empty.jar"
check "zero-byte archive fails" 1 "$RC"
# Empty ZIP central directory (no entries).
printf 'PK\005\006\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000' >"$WORK/empty-zip.jar"
run_guard "$WORK/empty-zip.jar"
check "empty ZIP fails" 1 "$RC"
run_guard "$WORK/no-classes.jar"
check "archive without classes fails" 1 "$RC"
contains "no classes diagnostic" 'no class files'
printf 'not a zip\n' >"$WORK/corrupt.jar"
run_guard "$WORK/corrupt.jar"
check "corrupt archive fails" 1 "$RC"
contains "corrupt archive diagnostic" 'corrupt archive'
cp "$WORK/corrupt.jar" "$WORK/corrupt.aar"
run_guard "$WORK/corrupt.aar"
check "corrupt outer AAR fails" 1 "$RC"

mkdir -p "$WORK/malformed"
printf '\312\376\272\276\000\000\000' >"$WORK/malformed/Truncated.class"
archive "$WORK/truncated.jar" "$WORK/malformed"
run_guard "$WORK/truncated.jar"
check "truncated class header fails" 1 "$RC"
contains "truncated class is named" 'Truncated.class: malformed class header'
printf '\000\000\000\000\000\000\000\075' >"$WORK/malformed/Truncated.class"
archive "$WORK/bad-magic.jar" "$WORK/malformed"
run_guard "$WORK/bad-magic.jar"
check "invalid class magic fails" 1 "$RC"
contains "invalid magic diagnostic" 'malformed class header'

archive "$WORK/no-classes-jar.aar" "$WORK/none"
run_guard "$WORK/no-classes-jar.aar"
check "AAR missing classes.jar fails" 1 "$RC"
contains "missing nested JAR diagnostic" 'missing or unreadable classes.jar'
: >"$WORK/aar/classes.jar"
archive "$WORK/empty-nested.aar" "$WORK/aar"
run_guard "$WORK/empty-nested.aar"
check "empty nested JAR fails" 1 "$RC"
cp "$WORK/corrupt.jar" "$WORK/aar/classes.jar"
archive "$WORK/corrupt-nested.aar" "$WORK/aar"
run_guard "$WORK/corrupt-nested.aar"
check "corrupt nested JAR fails" 1 "$RC"
contains "corrupt nested JAR is named" 'corrupt-nested.aar!classes.jar: empty or corrupt archive'
cp "$WORK/no-classes.jar" "$WORK/aar/classes.jar"
archive "$WORK/no-nested-classes.aar" "$WORK/aar"
run_guard "$WORK/no-nested-classes.aar"
check "nested JAR without classes fails" 1 "$RC"

cp "$WORK/java17.jar" "$WORK/publishable with spaces.jar"
run_guard "$WORK/publishable with spaces.jar"
check "archive paths containing spaces work" 0 "$RC"
cp "$WORK/java17.jar" "$WORK/unsupported.zip"
run_guard "$WORK/unsupported.zip"
check "unsupported archive extension fails" 1 "$RC"

echo "check-bytecode tests: $((N - FAILS))/$N passed"
[ "$FAILS" -eq 0 ]
