#!/usr/bin/env bash
# JitPack before_install step (see jitpack.yml): installs the Android SDK packages the build needs into a private
# SDK and points AGP at it with local.properties (sdk.dir wins over ANDROID_HOME), so the build does not depend on
# what JitPack's preinstalled SDK contains. Same pinned versions as scripts/setup-linux.sh.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=lib/android-sdk-versions.sh
. "$ROOT/scripts/lib/android-sdk-versions.sh"
SDK="${CHERKIT_ANDROID_SDK:-$HOME/cherkit-android-sdk}"
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$SDKMANAGER" ]; then
  TMP="$(mktemp -d)"
  trap 'rm -rf "$TMP"' EXIT
  curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS}_latest.zip" -o "$TMP/clt.zip"
  echo "$ANDROID_CMDLINE_TOOLS_SHA256  $TMP/clt.zip" | sha256sum -c --quiet
  unzip -q "$TMP/clt.zip" -d "$TMP"
  mkdir -p "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$TMP/cmdline-tools" "$SDK/cmdline-tools/latest"
fi
yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null || true
"$SDKMANAGER" --sdk_root="$SDK" "${ANDROID_PACKAGES[@]}" >/dev/null
echo "sdk.dir=$SDK" >"$ROOT/local.properties"
echo "Android SDK ready at $SDK (${ANDROID_PACKAGES[*]})"
