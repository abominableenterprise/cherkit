#!/usr/bin/env bash
# Linux must resolve no Firebase SDK while retaining the adapter's policy/fake tests.
# dump-package evaluates the manifest without fetching dependencies.
set -euo pipefail

if [ "$(uname -s)" != Linux ]; then
  echo "firebase-package tests: Linux-only graph check skipped"
  exit 0
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MANIFEST="$(mktemp)"
trap 'rm -f "$MANIFEST"' EXIT
swift package --package-path "$ROOT" dump-package >"$MANIFEST"
python3 - "$MANIFEST" <<'PY'
import json
import sys

with open(sys.argv[1]) as manifest:
    package = json.load(manifest)

assert "firebase-ios-sdk" not in json.dumps(package["dependencies"]).lower(), \
    "Linux must not resolve the Firebase package"
assert all(product["name"] != "CherKitTelemetryFirebase" for product in package["products"]), \
    "The Firebase library product is Apple-only"
targets = {target["name"]: target for target in package["targets"]}
assert targets["CherKitTelemetryFirebase"]["dependencies"] == [
    {"byName": ["CherKitTelemetry", None]},
    {"byName": ["CherKitCore", None]},
], "Linux must retain the Foundation-only adapter without Firebase products"
assert targets["CherKitTelemetryFirebaseTests"]["type"] == "test"
assert targets["CherKitTelemetryFirebaseTests"]["dependencies"] == [
    {"byName": ["CherKitTelemetryFirebase", None]},
    {"byName": ["CherKitTelemetry", None]},
    {"byName": ["CherKitCore", None]},
], "Linux must retain all recording-fake policy tests"
assert "firebase-ios-sdk" not in json.dumps(package["targets"]).lower(), \
    "No Linux target may depend on Firebase products"
print("firebase-package tests: Linux graph and policy/fake coverage passed")
PY
