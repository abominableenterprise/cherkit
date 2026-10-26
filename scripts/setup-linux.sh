#!/usr/bin/env bash
# Idempotent Linux setup for CherKit: Swift toolchain, JDK and Android SDK.
# Everything lands in $CHERKIT_TOOLS (default ~/.cherkit-tools); scripts/test.sh
# sources $CHERKIT_TOOLS/env.sh. Swift, the JDK and the Android SDK always come from
# upstream (checksums pinned; that is faster than a cache restore). Only Gradle's
# dependency cache (~/.gradle/caches/modules-2) and wrapper distributions
# (~/.gradle/wrapper/dists) are cached with the VM's `task-cache` when present
# (key = SHA-256 of the recipe); without it Gradle downloads normally. Never all of
# ~/.gradle: daemon logs, configuration cache and transforms only bloat it.
# Directories larger than CHERKIT_CACHE_MAX_MB (uncompressed, default 1500) are not
# saved, keeping archives under the hub's 2 GB compressed cap.
set -euo pipefail

SWIFT_VERSION="6.4.1"
JDK_VERSION="21.0.13+9" # Eclipse Temurin
JDK_SHA256_x64="d7f5d5cf865a2902b70ff549b97586a7d3f234e016f041ab447ea740c7d66d1c"
JDK_SHA256_aarch64="e3522e707fc7dbe2a72c30746b1e7068301a0213ecd213d4491f35a14eb55578"
ANDROID_CMDLINE_TOOLS="16111833"
ANDROID_CMDLINE_TOOLS_SHA256="0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8"
ANDROID_PACKAGES=("platforms;android-36" "build-tools;36.0.0")
RECIPE_REVISION="2" # bump to invalidate every cache key below

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="${CHERKIT_TOOLS:-$HOME/.cherkit-tools}"
START=$SECONDS
mkdir -p "$TOOLS"
TOOLS="$(cd "$TOOLS" && pwd)" # absolute, so env.sh works from any directory

# One setup at a time per tools directory: held until the script exits.
if command -v flock >/dev/null 2>&1; then
  exec 9>"$TOOLS/.setup.lock"
  flock 9
fi
# Staging directory of the install in progress; removed on any exit, including failures.
STAGE=""
trap 'if [ -n "$STAGE" ]; then rm -rf "$STAGE"; fi' EXIT

log() { printf '\n==> %s\n' "$*"; }
sha() { sha256sum | cut -d' ' -f1; }
has_cache() { command -v task-cache >/dev/null 2>&1; }
SUDO=""
[ "$(id -u)" -eq 0 ] || SUDO="sudo"

# cache_restore <key> <dir>: returns 0 only if <dir> was restored. Restores into a private
# staging directory next to <dir> and moves it into place only if <dir> still does not exist,
# so a failure never deletes or overwrites a <dir> another process created meanwhile.
cache_restore() {
  has_cache || return 1
  [ -e "$2" ] && return 1
  # Explicit returns: callers use this in a condition, which disables errexit in here.
  mkdir -p "$(dirname "$2")" || return 1
  STAGE="$(mktemp -d "$(dirname "$2")/.cherkit-restore.XXXXXX")" || { STAGE=""; return 1; }
  [ -d "$STAGE" ] || { STAGE=""; return 1; }
  local rc=1
  if task-cache restore "$1" "$STAGE/data" && mv -T -n "$STAGE/data" "$2" && [ ! -e "$STAGE/data" ]; then
    rc=0
  fi
  rm -rf "$STAGE"
  STAGE=""
  return "$rc"
}
cache_save() {
  has_cache || return 0
  [ -d "$2" ] || { echo "not caching $2: missing"; return 0; }
  local mb
  mb="$(du -sm "$2" | cut -f1)"
  if [ "$mb" -gt "${CHERKIT_CACHE_MAX_MB:-1500}" ]; then
    echo "not caching $2: ${mb} MB > CHERKIT_CACHE_MAX_MB=${CHERKIT_CACHE_MAX_MB:-1500}"
    return 0
  fi
  echo "saving $2 (${mb} MB) to task-cache"
  task-cache save "$1" "$2" || echo "warning: task-cache save failed for $2 (continuing)"
}

ARCH="$(uname -m)"
case "$ARCH" in
  x86_64) JDK_ARCH="x64" ;;
  aarch64 | arm64) ARCH="aarch64"; JDK_ARCH="aarch64" ;;
  *) echo "unsupported architecture: $ARCH" >&2; exit 1 ;;
esac
. /etc/os-release
case "${ID}-${VERSION_ID}" in
  ubuntu-24.04) SWIFT_OS="ubuntu24.04"; GCC=13 ;;
  ubuntu-22.04) SWIFT_OS="ubuntu22.04"; GCC=12 ;;
  debian-12) SWIFT_OS="debian12"; GCC=12 ;;
  *) echo "warning: ${ID} ${VERSION_ID} untested, using the Ubuntu 24.04 toolchain"; SWIFT_OS="ubuntu24.04"; GCC=13 ;;
esac

log "System packages"
PKGS=(binutils ca-certificates curl git gnupg2 libc6-dev libcurl4-openssl-dev libedit2 "libgcc-${GCC}-dev"
  libncurses-dev libpython3-dev libsqlite3-0 "libstdc++-${GCC}-dev" libxml2-dev libz3-dev pkg-config
  tzdata unzip zlib1g-dev)
MISSING=()
for p in "${PKGS[@]}"; do
  dpkg-query -W -f='${Status}' "$p" 2>/dev/null | grep -q "install ok installed" || MISSING+=("$p")
done
if [ ${#MISSING[@]} -gt 0 ]; then
  $SUDO apt-get update -qq
  DEBIAN_FRONTEND=noninteractive $SUDO apt-get install -y -qq --no-install-recommends "${MISSING[@]}"
else
  echo "all present"
fi

# --- Swift -------------------------------------------------------------------
SWIFT_DIR="$TOOLS/swift-$SWIFT_VERSION-$SWIFT_OS-$ARCH"
log "Swift $SWIFT_VERSION ($SWIFT_OS, $ARCH)"
if [ -x "$SWIFT_DIR/usr/bin/swift" ]; then
  echo "already installed"
else
  rm -rf "$SWIFT_DIR"
  SUFFIX=""; [ "$ARCH" = "aarch64" ] && SUFFIX="-aarch64"
  PLATFORM="${SWIFT_OS//./}$SUFFIX"
  NAME="swift-$SWIFT_VERSION-RELEASE-$SWIFT_OS$SUFFIX"
  URL="https://download.swift.org/swift-$SWIFT_VERSION-release/$PLATFORM/swift-$SWIFT_VERSION-RELEASE/$NAME.tar.gz"
  STAGE="$(mktemp -d "$TOOLS/.staging.XXXXXX")"
  TMP="$STAGE"
  curl -fsSL "$URL" -o "$TMP/swift.tar.gz"
  curl -fsSL "$URL.sig" -o "$TMP/swift.tar.gz.sig"
  export GNUPGHOME="$TMP/gnupg"; mkdir -m 700 "$GNUPGHOME"
  curl -fsSL --compressed https://www.swift.org/keys/all-keys.asc | gpg --batch --quiet --import
  gpg --batch --verify "$TMP/swift.tar.gz.sig" "$TMP/swift.tar.gz"
  unset GNUPGHOME
  mkdir "$TMP/swift"
  tar -xzf "$TMP/swift.tar.gz" -C "$TMP/swift" --strip-components=1
  mv "$TMP/swift" "$SWIFT_DIR"
  rm -rf "$TMP"
  STAGE=""
fi

# --- JDK + Android SDK ---------------------------------------------------------
JA_RECIPE="cherkit-jdk-android|temurin-$JDK_VERSION|cmdline-$ANDROID_CMDLINE_TOOLS|${ANDROID_PACKAGES[*]}|$ARCH|$RECIPE_REVISION"
JA_DIR="$TOOLS/jdk-android-$(echo "$JA_RECIPE" | sha | cut -c1-12)"
log "JDK $JDK_VERSION + Android SDK (${ANDROID_PACKAGES[*]})"
if [ -f "$JA_DIR/.complete" ]; then
  echo "already installed"
else
  rm -rf "$JA_DIR"
  STAGE="$(mktemp -d "$TOOLS/.staging.XXXXXX")"
  TMP="$STAGE"
  mkdir -p "$TMP/out/jdk" "$TMP/out/android-sdk/cmdline-tools"
  JDK_FEATURE="${JDK_VERSION%%.*}"
  JDK_FILE="OpenJDK${JDK_FEATURE}U-jdk_${JDK_ARCH}_linux_hotspot_${JDK_VERSION/+/_}.tar.gz"
  JDK_URL="https://github.com/adoptium/temurin${JDK_FEATURE}-binaries/releases/download/jdk-${JDK_VERSION/+/%2B}/$JDK_FILE"
  JDK_SHA_VAR="JDK_SHA256_$JDK_ARCH"
  curl -fsSL "$JDK_URL" -o "$TMP/jdk.tar.gz"
  echo "${!JDK_SHA_VAR}  $TMP/jdk.tar.gz" | sha256sum -c --quiet
  tar -xzf "$TMP/jdk.tar.gz" -C "$TMP/out/jdk" --strip-components=1
  curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS}_latest.zip" -o "$TMP/clt.zip"
  echo "$ANDROID_CMDLINE_TOOLS_SHA256  $TMP/clt.zip" | sha256sum -c --quiet
  unzip -q "$TMP/clt.zip" -d "$TMP/clt"
  mv "$TMP/clt/cmdline-tools" "$TMP/out/android-sdk/cmdline-tools/latest"
  SDKMANAGER="$TMP/out/android-sdk/cmdline-tools/latest/bin/sdkmanager"
  export JAVA_HOME="$TMP/out/jdk"
  yes | "$SDKMANAGER" --sdk_root="$TMP/out/android-sdk" --licenses >/dev/null || true
  "$SDKMANAGER" --sdk_root="$TMP/out/android-sdk" "${ANDROID_PACKAGES[@]}" >/dev/null
  touch "$TMP/out/.complete"
  mv "$TMP/out" "$JA_DIR"
  rm -rf "$TMP"
  STAGE=""
fi

# --- env.sh --------------------------------------------------------------------
# Paths are written with printf %q so characters like $, quotes or spaces stay literal.
# shellcheck disable=SC2016 # single-quoted lines are meant to expand when env.sh is sourced
{
  printf '# Generated by scripts/setup-linux.sh. Source it from bash: . %q\n' "$TOOLS/env.sh"
  printf 'export JAVA_HOME=%q\n' "$JA_DIR/jdk"
  printf 'export ANDROID_HOME=%q\n' "$JA_DIR/android-sdk"
  echo 'export ANDROID_SDK_ROOT="$ANDROID_HOME"'
  echo 'case ":$PATH:" in *":$JAVA_HOME/bin:"*) ;; *) export PATH="$JAVA_HOME/bin:$PATH" ;; esac'
  printf 'case ":$PATH:" in *:%q:*) ;; *) export PATH=%q:"$PATH" ;; esac\n' "$SWIFT_DIR/usr/bin" "$SWIFT_DIR/usr/bin"
} >"$TOOLS/env.sh"
. "$TOOLS/env.sh"

# --- Gradle cache (modules-2 + wrapper dists) -------------------------------------
log "Gradle cache"
GRADLE_RECIPE="$(cd "$ROOT" && {
  echo "cherkit-gradle|$ARCH|$JA_RECIPE|$RECIPE_REVISION"
  git ls-files --cached --others --exclude-standard -- '*.gradle.kts' 'gradle/*' 'gradle.properties' \
    | sort | xargs sha256sum
} | sha)"
GRADLE_HOME_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}"
# One task-cache entry per directory, each with its own key.
GRADLE_NAMES=(modules-2 wrapper-dists)
GRADLE_DIRS=("$GRADLE_HOME_DIR/caches/modules-2" "$GRADLE_HOME_DIR/wrapper/dists")
GRADLE_KEYS=()
for name in "${GRADLE_NAMES[@]}"; do
  GRADLE_KEYS+=("$(echo "cherkit-gradle-$name|$GRADLE_RECIPE" | sha)")
done
TO_SAVE=() # indexes of the entries not restored
for i in "${!GRADLE_NAMES[@]}"; do
  t=$SECONDS
  if cache_restore "${GRADLE_KEYS[$i]}" "${GRADLE_DIRS[$i]}"; then
    echo "${GRADLE_NAMES[$i]}: restored $(du -sm "${GRADLE_DIRS[$i]}" | cut -f1) MB from task-cache in $((SECONDS - t))s"
  else
    if [ -e "${GRADLE_DIRS[$i]}" ]; then
      echo "${GRADLE_NAMES[$i]}: already present"
    else
      echo "${GRADLE_NAMES[$i]}: not restored"
    fi
    TO_SAVE+=("$i")
  fi
done
# Resolve every dependency and compile once, so later test runs only build our code.
(cd "$ROOT" && ./gradlew --quiet jvmTestClasses compileAndroidHostTest)
if [ ${#TO_SAVE[@]} -gt 0 ] && has_cache; then
  # Stop the daemon so nothing writes to the caches while they are archived.
  (cd "$ROOT" && ./gradlew --quiet --stop)
  for i in "${TO_SAVE[@]}"; do
    t=$SECONDS
    cache_save "${GRADLE_KEYS[$i]}" "${GRADLE_DIRS[$i]}"
    echo "${GRADLE_NAMES[$i]}: save step took $((SECONDS - t))s"
  done
fi

log "Done in $((SECONDS - START))s"
swift --version 2>&1 | head -1
java -version 2>&1 | head -1
echo "Run: scripts/test.sh [analytics|sunset|whatsnew]"
