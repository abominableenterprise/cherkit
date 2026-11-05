#!/usr/bin/env bash
# Check optional SDK registration without fetching dependencies, on Apple hosts and Linux.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MANIFEST="$(mktemp)"
trap 'rm -f "$MANIFEST"' EXIT
swift package --package-path "$ROOT" dump-package >"$MANIFEST"
python3 - "$MANIFEST" "$(uname -s)" <<'PY'
import json
import sys

with open(sys.argv[1]) as manifest:
    package = json.load(manifest)
targets = {target["name"]: target for target in package["targets"]}
products = {product["name"] for product in package["products"]}
for name, base in [
    ("CherKitAttributionRevenueCat", "CherKitCore"),
    ("CherKitAttributionMeta", "CherKitAttributionRevenueCat"),
]:
    assert name in products
    assert {"byName": [base, None]} in targets[name]["dependencies"]
    assert targets[name + "Tests"]["type"] == "test"
    assert targets[name + "Tests"]["dependencies"] == [{"byName": [name, None]}]

vendors = ("purchases-ios", "facebook-ios-sdk")
if sys.argv[2] == "Linux":
    for vendor in vendors:
        assert vendor not in json.dumps(package).lower(), "Linux must not resolve " + vendor
else:
    for vendor in vendors:
        assert vendor in json.dumps(package["dependencies"]).lower()
    rc = targets["CherKitAttributionRevenueCat"]["dependencies"]
    meta = targets["CherKitAttributionMeta"]["dependencies"]
    assert [dep["product"][:2] for dep in rc if "product" in dep] == [["RevenueCat", "purchases-ios"]]
    assert [dep["product"][:2] for dep in meta if "product" in dep] == [["FacebookCore", "facebook-ios-sdk"]]
    assert next(dep["product"][-1] for dep in meta if "product" in dep) == {"platformNames": ["ios"]}
print("attribution-package tests: optional SDK graph and test registration passed")
PY
