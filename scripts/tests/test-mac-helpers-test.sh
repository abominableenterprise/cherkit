#!/usr/bin/env bash
# Checks the parsing helpers of scripts/test-mac.sh (scripts/lib/test-mac-helpers.sh) with sample
# inputs; runs on Linux and macOS. Run: scripts/tests/test-mac-helpers-test.sh
set -euo pipefail

SCRIPTS="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=../lib/test-mac-helpers.sh
. "$SCRIPTS/lib/test-mac-helpers.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
FAILS=0
N=0

check() { # check <label> <want> <got>
  N=$((N + 1))
  if [ "$2" = "$3" ]; then echo "ok   $1"; else echo "FAIL $1: want [$2], got [$3]"; FAILS=$((FAILS + 1)); fi
}

# --- java_major_parse: real `java -version` first lines.
major() { printf '%s\n' "$1" 'OpenJDK Runtime Environment (build x)' | java_major_parse; }
check "JDK 17.0.2" 17 "$(major 'openjdk version "17.0.2" 2022-01-18')"
check "JDK 21 (no minor)" 21 "$(major 'openjdk version "21" 2023-09-19')"
check "JDK 27-ea" 27 "$(major 'openjdk version "27-ea" 2026-09-15')"
check "JDK 26-internal" 26 "$(major 'openjdk version "26-internal" 2026-03-17')"
check "JDK 25+36" 25 "$(major 'openjdk version "25+36" 2025-09-16')"
check "Oracle java 21.0.5" 21 "$(major 'java version "21.0.5" 2024-10-15 LTS')"
check "legacy 1.8.0_392" 8 "$(major 'openjdk version "1.8.0_392"')"
check "Picked up _JAVA_OPTIONS line first" 17 \
  "$(printf '%s\n' 'Picked up _JAVA_OPTIONS: -Xmx1g' 'openjdk version "17.0.9" 2023-10-17' | java_major_parse)"
check "no version line" "" "$(printf 'Error: could not find libjava.dylib\n' | java_major_parse)"

# java_major runs the binary: a fake `java` that prints an early-access version.
cat >"$WORK/java" <<'SH'
#!/bin/sh
echo 'openjdk version "27-ea" 2026-09-15' >&2
SH
chmod +x "$WORK/java"
check "java_major on a 27-ea binary" 27 "$(java_major "$WORK/java")"

# --- properties_value: Java .properties decoding of sdk.dir.
props() { printf '%s' "$1" >"$WORK/local.properties"; properties_value "$WORK/local.properties" sdk.dir; }
check "plain path" "/Users/p/Library/Android/sdk" "$(props $'sdk.dir=/Users/p/Library/Android/sdk\n')"
check "escaped space" "/Volumes/External SSD/Android/sdk" "$(props $'sdk.dir=/Volumes/External\\ SSD/Android/sdk\n')"
check "escaped colon and backslash (Windows style)" 'C:\Users\p\AppData\Local\Android\Sdk' \
  "$(props $'sdk.dir=C\\:\\\\Users\\\\p\\\\AppData\\\\Local\\\\Android\\\\Sdk\n')"
check "\\uXXXX escape" "/Users/josé/sdk" "$(props $'sdk.dir=/Users/jos\\u00e9/sdk\n')"
check "spaces around = and colon separator" "/opt/sdk" "$(props $'  sdk.dir  :  /opt/sdk\n')"
check "whitespace separator" "/opt/sdk" "$(props $'sdk.dir /opt/sdk\n')"
check "comments and other keys ignored" "/opt/sdk" \
  "$(props $'# sdk.dir=/wrong\n! sdk.dir=/wrong\nndk.dir=/ndk\nsdk.dir=/opt/sdk\nsdk.dirx=/wrong\n')"
check "continuation line" "/opt/android/sdk" "$(props $'sdk.dir=/opt/\\\n    android/sdk\n')"
check "escaped trailing backslash is not a continuation" '/opt/sdk\' "$(props $'sdk.dir=/opt/sdk\\\\\nother=x\n')"
check "CRLF line endings" "/opt/sdk" "$(props $'sdk.dir=/opt/sdk\r\nx=y\r\n')"
check "last occurrence wins" "/b" "$(props $'sdk.dir=/a\nsdk.dir=/b\n')"
check "no trailing newline" "/opt/sdk" "$(props 'sdk.dir=/opt/sdk')"
check "UTF-8 file" "/Users/josé/sdk" "$(props $'sdk.dir=/Users/josé/sdk\n')"
check "key missing" "" "$(props $'ndk.dir=/ndk\n')"
check "file missing" "" "$(properties_value "$WORK/nope.properties" sdk.dir)"

# --- pick_android_sdk + use_android_sdk.
mkdir -p "$WORK/sdk a/platforms" "$WORK/sdk b/platforms" "$WORK/External SSD/sdk/platforms" "$WORK/default/platforms"
printf 'sdk.dir=%s\n' "$(printf '%s' "$WORK/External SSD/sdk" | sed 's/ /\\ /g')" >"$WORK/local.properties"
check "sdk.dir with an escaped space is found" "$WORK/External SSD/sdk" \
  "$(unset ANDROID_HOME ANDROID_SDK_ROOT; pick_android_sdk "$WORK/local.properties" "$WORK/none")"
check "ANDROID_HOME wins" "$WORK/sdk a" \
  "$(ANDROID_HOME="$WORK/sdk a" ANDROID_SDK_ROOT="$WORK/sdk b" pick_android_sdk "$WORK/local.properties" "$WORK/default")"
check "ANDROID_SDK_ROOT next" "$WORK/sdk b" \
  "$(unset ANDROID_HOME; ANDROID_SDK_ROOT="$WORK/sdk b" pick_android_sdk "$WORK/local.properties" "$WORK/default")"
check "ANDROID_HOME without platforms/ is skipped" "$WORK/External SSD/sdk" \
  "$(unset ANDROID_SDK_ROOT; ANDROID_HOME="$WORK/nope" pick_android_sdk "$WORK/local.properties" "$WORK/default")"
check "default dir last" "$WORK/default" \
  "$(unset ANDROID_HOME ANDROID_SDK_ROOT; pick_android_sdk "$WORK/none.properties" "$WORK/default")"
check "nothing found" "" \
  "$(unset ANDROID_HOME ANDROID_SDK_ROOT; pick_android_sdk "$WORK/none.properties" "$WORK/none")"
check "use_android_sdk sets both variables to the selected SDK" "$WORK/sdk a|$WORK/sdk a" \
  "$(ANDROID_HOME="$WORK/sdk a" ANDROID_SDK_ROOT="$WORK/sdk b"
     use_android_sdk "$(pick_android_sdk "$WORK/local.properties" "$WORK/default")"
     bash -c 'printf "%s|%s" "$ANDROID_HOME" "$ANDROID_SDK_ROOT"')"

echo "test-mac helper tests: $((N - FAILS))/$N passed"
[ "$FAILS" -eq 0 ]
