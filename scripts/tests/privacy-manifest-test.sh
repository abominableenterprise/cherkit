#!/usr/bin/env bash
# A tiny standalone package tests discovery, missing resources and manifest contents.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TEMP="$(mktemp -d)"
trap 'rm -rf "$TEMP"' EXIT
mkdir -p "$TEMP/Sources/Example"
printf 'import Foundation\nlet defaults = UserDefaults.standard\n' > "$TEMP/Sources/Example/Store.swift"
printf '// swift-tools-version: 5.9\nimport PackageDescription\nlet package = Package(name: "Example", targets: [.target(name: "Example")])\n' > "$TEMP/Package.swift"
expect_failure() {
  if "$ROOT/scripts/privacy-manifest-check" "$TEMP" > "$TEMP/result" 2>&1; then
    echo "Expected privacy manifest check to fail: $1" >&2; exit 1
  fi
  grep -q "$1" "$TEMP/result"
}
expect_failure 'declare .copy'
cp "$ROOT/Sources/CherKitSunset/PrivacyInfo.xcprivacy" "$TEMP/Sources/Example/"
# A file alone is insufficient.
expect_failure 'declare .copy'
printf '// swift-tools-version: 5.9\nimport PackageDescription\nlet package = Package(name: "Example", targets: [.target(name: "Example", resources: [.copy("PrivacyInfo.xcprivacy")])])\n' > "$TEMP/Package.swift"
"$ROOT/scripts/privacy-manifest-check" "$TEMP"
python3 - "$TEMP/Sources/Example/PrivacyInfo.xcprivacy" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
path.write_text(path.read_text().replace('<string>1C8F.1</string>', ''))
PY
expect_failure 'declare UserDefaults reasons'
rm "$TEMP/Sources/Example/PrivacyInfo.xcprivacy"
expect_failure 'cannot read privacy manifest'
echo 'Privacy manifest regression tests passed'
