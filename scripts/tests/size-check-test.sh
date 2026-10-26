#!/usr/bin/env bash
# Checks scripts/size-check in throwaway git repos (a copy of the script in each), so nothing here
# touches the real checkout. Run: scripts/tests/size-check-test.sh
set -euo pipefail

SCRIPTS="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
FAILS=0
N=0

# new_repo <name>: a git repo with size-check, an empty baseline and one commit; prints its path.
new_repo() {
  local repo="$WORK/$1"
  mkdir -p "$repo/scripts"
  cp "$SCRIPTS/size-check" "$repo/scripts/size-check"
  printf '{\n  "files": {}\n}\n' >"$repo/size-baseline.json"
  git -C "$repo" init -q -b main
  git -C "$repo" -c user.name=t -c user.email=t@t add -A
  git -C "$repo" -c user.name=t -c user.email=t@t commit -q -m init
  echo "$repo"
}

commit_all() { git -C "$1" -c user.name=t -c user.email=t@t add -A && git -C "$1" -c user.name=t -c user.email=t@t commit -q -m "$2"; }

oversize() { python3 -c 'import sys; open(sys.argv[1], "w").write("x\n" * 801)' "$1"; }

# expect <exit code> <output pattern> <label> <repo> [size-check args...]
expect() {
  local want="$1" pattern="$2" label="$3" repo="$4" rc=0 out
  shift 4
  N=$((N + 1))
  out="$("$repo/scripts/size-check" "$@" 2>&1)" || rc=$?
  if [ "$rc" -eq "$want" ] && printf '%s' "$out" | grep -q -- "$pattern"; then
    echo "ok   $label"
  else
    echo "FAIL $label: exit $rc (want $want), output:"; printf '%s\n' "$out" | sed 's/^/     /'
    FAILS=$((FAILS + 1))
  fi
}

# 1. Non-ASCII and special-character names are checked, not skipped (git quotes them without -z).
r="$(new_repo names)"
expect 0 "size-check: ok" "clean repo passes" "$r"
oversize "$r/Größe.swift"
expect 1 "Größe.swift: 801 lines" "untracked non-ASCII oversize file fails" "$r"
commit_all "$r" "add"
expect 1 "Größe.swift: 801 lines" "tracked non-ASCII oversize file fails" "$r"
rm "$r/Größe.swift"
oversize "$r/tab	and \"quote\".md"
expect 1 "tab	and \"quote\".md: 801 lines" "oversize file with a tab and quotes in its name fails" "$r"
rm "$r/tab	and \"quote\".md"
mkdir -p "$r/Sources/Ünïcode dir"
oversize "$r/Sources/Ünïcode dir/日本.kt"
expect 1 "Sources/Ünïcode dir/日本.kt: 801 lines" "oversize file in a non-ASCII directory fails" "$r"
rm -rf "$r/Sources"
printf 'small\n' >"$r/Größe.swift"
expect 0 "size-check: ok" "small non-ASCII file passes" "$r"
# A baseline entry keyed by the real (unquoted) name applies to it.
git -C "$r" rm -q --cached Größe.swift
oversize "$r/Größe.swift"
printf '{"files": {"Größe.swift": {"lines": 900, "bytes": 2000}}}\n' >"$r/size-baseline.json"
expect 0 "size-check: ok" "baseline entry for a non-ASCII name applies" "$r"

# 2. Baseline comparison against a ref.
r="$(new_repo baseline)"
git -C "$r" branch base
expect 0 "size-check: ok" "unchanged baseline passes against a ref" "$r" --check-baseline-against base
oversize "$r/Big.swift"
printf '{"files": {"Big.swift": {"lines": 801, "bytes": 1602}}}\n' >"$r/size-baseline.json"
expect 1 "new entry Big.swift" "new baseline entry fails against a ref" "$r" --check-baseline-against base
commit_all "$r" "allow Big"
git -C "$r" branch with-big
printf '{"files": {"Big.swift": {"lines": 900, "bytes": 1602}}}\n' >"$r/size-baseline.json"
expect 1 "Big.swift limit raised above with-big" "raised limit fails" "$r" --check-baseline-against with-big
printf '{"files": {"Big.swift": {"lines": 801, "bytes": 1602}}}\n' >"$r/size-baseline.json"
expect 0 "size-check: ok" "unchanged entry passes" "$r" --check-baseline-against with-big

# A ref without size-baseline.json counts as an empty baseline: new entries still fail.
git -C "$r" checkout -q --orphan no-baseline
git -C "$r" rm -q --cached size-baseline.json
git -C "$r" -c user.name=t -c user.email=t@t commit -q -m "no baseline"
git -C "$r" checkout -q -f main
expect 1 "new entry Big.swift" "ref without a baseline file = empty baseline (new entry fails)" \
  "$r" --check-baseline-against no-baseline
printf '{\n  "files": {}\n}\n' >"$r/size-baseline.json"
rm "$r/Big.swift"
expect 0 "size-check: ok" "ref without a baseline file, empty local baseline passes" \
  "$r" --check-baseline-against no-baseline

# Any other failure is an error, never a skipped check.
expect 1 "not a valid git ref" "unknown ref is an error" "$r" --check-baseline-against origin/nope
expect 1 "not a valid git ref" "malformed ref is an error" "$r" --check-baseline-against 'main:bad'
git -C "$r" checkout -q -b broken
printf 'not json\n' >"$r/size-baseline.json"
commit_all "$r" "broken baseline"
git -C "$r" checkout -q main
expect 1 "is not a valid baseline" "unreadable baseline at the ref is an error" "$r" --check-baseline-against broken
git -C "$r" checkout -q -b dir-baseline
rm "$r/size-baseline.json"
mkdir "$r/size-baseline.json"
touch "$r/size-baseline.json/x"
commit_all "$r" "baseline is a directory"
git -C "$r" checkout -q main
expect 1 "is not a valid baseline\|cannot read" "baseline path that is a directory at the ref is an error" \
  "$r" --check-baseline-against dir-baseline
expect 1 "usage:" "bad arguments are rejected" "$r" --bogus

echo "size-check tests: $((N - FAILS))/$N passed"
[ "$FAILS" -eq 0 ]
