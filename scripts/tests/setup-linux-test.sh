#!/usr/bin/env bash
# Exercise setup with fake packages, toolchains and task-cache; no downloads or builds.
set -euo pipefail

if [ "$(uname -s)" != Linux ]; then
  echo "setup-linux tests: Linux-only setup check skipped"
  exit 0
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/repo/scripts/lib" "$WORK/repo/gradle/wrapper" "$WORK/bin" "$WORK/home"
cp "$ROOT/scripts/setup-linux.sh" "$WORK/repo/scripts/"
cp "$ROOT/scripts/lib/"{android-sdk-versions,gradle-warmup}.sh "$WORK/repo/scripts/lib/"
cp "$ROOT/gradle/libs.versions.toml" "$WORK/repo/gradle/"
cp "$ROOT/gradle/wrapper/gradle-wrapper.properties" "$WORK/repo/gradle/wrapper/"
git -C "$WORK/repo" init -q
export HOME="$WORK/home" CHERKIT_TOOLS="$WORK/tools" GRADLE_USER_HOME="$WORK/gradle"
export CALLS="$WORK/calls"
export PATH="$WORK/bin:$PATH"
unset CHERKIT_GRADLE_WARMUP
cat >"$WORK/bin/dpkg-query" <<'SH'
#!/usr/bin/env bash
echo 'install ok installed'
echo package >>"$CALLS"
SH
cat >"$WORK/bin/task-cache" <<'SH'
#!/usr/bin/env bash
echo "$1 $2" >>"$CALLS"
[ "$1" = restore ] || exit 0
[ "${MISS:-0}" = 0 ] || exit 3
mkdir -p "$3/usr/bin" "$3/jdk/bin"
touch "$3/.complete"
printf '#!/usr/bin/env bash\necho stub-swift\n' >"$3/usr/bin/swift"
printf '#!/usr/bin/env bash\necho stub-java\n' >"$3/jdk/bin/java"
chmod +x "$3/usr/bin/swift" "$3/jdk/bin/java"
SH
cat >"$WORK/repo/gradlew" <<'SH'
#!/usr/bin/env bash
echo "gradlew $*" >>"$CALLS"
case "$*" in *--stop*) exit 0 ;; esac
mkdir -p "$GRADLE_USER_HOME/caches/modules-2" "$GRADLE_USER_HOME/wrapper/dists"
exit "${BUILD_RC:-0}"
SH
chmod +x "$WORK/bin/"* "$WORK/repo/gradlew"
FAILS=0
N=0
check() {
  N=$((N + 1))
  if [ "$2" = "$3" ]; then echo "ok   $1"; else echo "FAIL $1: want [$2], got [$3]"; FAILS=$((FAILS + 1)); fi
}
run_setup() {
  : >"$CALLS"
  bash "$WORK/repo/scripts/setup-linux.sh" >"$WORK/out" 2>&1
}
count() { grep -c "$1" "$CALLS" || true; }
keys() { grep '^restore ' "$CALLS" | tail -n 2; }
reset_gradle() { rm -rf "$GRADLE_USER_HOME"; }

# Simulate macOS and reject fixture creation: unsupported hosts must skip before setup.
mkdir -p "$WORK/non-linux-bin"
printf '#!/usr/bin/env bash\necho Darwin\n' >"$WORK/non-linux-bin/uname"
printf '#!/usr/bin/env bash\necho "unexpected fixture creation" >&2\nexit 1\n' >"$WORK/non-linux-bin/mktemp"
chmod +x "$WORK/non-linux-bin/"*
PLATFORM_RC=0
PATH="$WORK/non-linux-bin:$PATH" bash "$ROOT/scripts/tests/setup-linux-test.sh" >"$WORK/platform-out" 2>&1 || PLATFORM_RC=$?
check 'non-Linux host exits successfully before fixtures' 0 "$PLATFORM_RC"
check 'non-Linux host reports skip' 'setup-linux tests: Linux-only setup check skipped' "$(<"$WORK/platform-out")"

run_setup
BASE_KEYS="$(keys)"
check 'restores all four entries' 4 "$(count '^restore ')"
check 'restored caches skip compile' 0 "$(count '^gradlew ')"
check 'restored caches skip save' 0 "$(count '^save ')"
run_setup
check 'warm VM exits early' 1 "$(grep -c 'already set up' "$WORK/out" || true)"
check 'warm VM still checks packages' yes "$([ "$(count '^package')" -gt 0 ] && echo yes || echo no)"
check 'warm VM does not access cache or Gradle' 0 "$(count 'restore\|save\|gradlew')"
CHERKIT_GRADLE_WARMUP=1 run_setup
check 'opt-in overrides early exit and compiles' 1 "$(count 'jvmTestClasses compileAndroidHostTest')"

# Existing Gradle directories also skip compilation when env.sh needs regeneration.
rm "$CHERKIT_TOOLS/env.sh"
run_setup
check 'existing caches skip compile' 0 "$(count '^gradlew ')"
check 'existing caches skip save' 0 "$(count '^save ')"
rm -rf "$GRADLE_USER_HOME/caches/modules-2"
MISS=1 run_setup
check 'one missing entry compiles' 1 "$(count 'jvmTestClasses compileAndroidHostTest')"
check 'only the missing entry is saved' 1 "$(count '^save ')"
rm -rf "$GRADLE_USER_HOME/wrapper/dists"
MISS=1 BUILD_RC=1 run_setup
check 'failed compile warns without aborting setup' 1 "$(grep -c 'warning: gradle warm-up failed' "$WORK/out" || true)"
check 'failed compile does not save' 0 "$(count '^save ')"

# Only the catalog affects modules-2; only wrapper properties affect wrapper-dists.
printf 'version=99\n' >"$WORK/repo/gradle.properties"
printf '// new module\n' >"$WORK/repo/build.gradle.kts"
reset_gradle
run_setup
check 'release and module edits preserve both keys' "$BASE_KEYS" "$(keys)"
echo '# catalog change' >>"$WORK/repo/gradle/libs.versions.toml"
run_setup
check 'recipe change bypasses early exit' 0 "$(grep -c 'already set up' "$WORK/out" || true)"
reset_gradle
run_setup
CATALOG_KEYS="$(keys)"
check 'catalog changes dependency key' no "$([ "$(head -n 1 <<<"$BASE_KEYS")" = "$(head -n 1 <<<"$CATALOG_KEYS")" ] && echo yes || echo no)"
check 'catalog preserves wrapper key' "$(tail -n 1 <<<"$BASE_KEYS")" "$(tail -n 1 <<<"$CATALOG_KEYS")"
echo '# wrapper change' >>"$WORK/repo/gradle/wrapper/gradle-wrapper.properties"
reset_gradle
run_setup
check 'wrapper preserves dependency key' "$(head -n 1 <<<"$CATALOG_KEYS")" "$(keys | head -n 1)"
check 'wrapper changes distribution key' no "$([ "$(tail -n 1 <<<"$CATALOG_KEYS")" = "$(keys | tail -n 1)" ] && echo yes || echo no)"

echo "setup-linux tests: $((N - FAILS))/$N passed"
[ "$FAILS" -eq 0 ]
