#!/usr/bin/env bash
# Check only the explicitly supplied publishable JARs / AAR classes.jar files.
# Java 17's class-file major version (61) is the highest supported version.
set -euo pipefail

if [ "$#" -eq 0 ]; then
  echo "Usage: $0 <publishable.jar|publishable.aar> [...]" >&2
  exit 2
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

valid_archive() {
  if ! unzip -tqq "$1" >"$WORK/unzip.log" 2>&1; then
    echo "ERROR: $2: empty or corrupt archive" >&2
    return 1
  fi
}

scan_jar() {
  local archive="$1" label="$2" entry pattern header major maximum=0 count=0 failed=0
  local -a bytes
  valid_archive "$archive" "$label" || return 1
  if ! unzip -Z1 "$archive" >"$WORK/entries"; then
    echo "ERROR: $label: cannot list archive" >&2
    return 1
  fi
  while IFS= read -r entry; do
    case "$entry" in *.class) ;; *) continue ;; esac
    # unzip treats entry names as globs; escape them to read exactly this class.
    pattern="${entry//\\/\\\\}"
    pattern="${pattern//\*/\\*}"
    pattern="${pattern//\?/\\?}"
    pattern="${pattern//\[/\\[}"
    if ! unzip -p "$archive" "$pattern" >"$WORK/class.bin"; then
      echo "ERROR: $label!$entry: cannot read class" >&2
      return 1
    fi
    if ! header="$(od -An -v -t u1 -N8 "$WORK/class.bin")"; then
      echo "ERROR: $label!$entry: cannot read class header" >&2
      return 1
    fi
    read -r -a bytes <<<"$header"
    if [ "${#bytes[@]}" -ne 8 ] || [ "${bytes[*]:0:4}" != "202 254 186 190" ]; then
      echo "ERROR: $label!$entry: malformed class header" >&2
      return 1
    fi
    # Class-file bytes 6–7 are the unsigned, big-endian major version.
    major=$((bytes[6] * 256 + bytes[7]))
    if [ "$major" -lt 45 ]; then
      echo "ERROR: $label!$entry: invalid class major version $major" >&2
      return 1
    fi
    count=$((count + 1))
    if [ "$major" -gt "$maximum" ]; then maximum="$major"; fi
    if [ "$major" -gt 61 ]; then
      echo "ERROR: $label!$entry: class major version $major exceeds 61 (Java 17)" >&2
      failed=1
    fi
  done <"$WORK/entries"
  if [ "$count" -eq 0 ]; then
    echo "ERROR: $label: no class files" >&2
    return 1
  fi
  echo "$label: maximum class major version $maximum (limit 61)"
  return "$failed"
}

failed=0
for path in "$@"; do
  if [ ! -f "$path" ] || [ ! -s "$path" ]; then
    echo "ERROR: $path: missing or empty archive" >&2
    failed=1
    continue
  fi
  # Absolute paths also prevent unzip from interpreting leading '-' as options.
  case "$path" in /*) archive="$path" ;; *) archive="$PWD/$path" ;; esac
  case "$path" in
    *.jar)
      if ! scan_jar "$archive" "$path"; then failed=1; fi
      ;;
    *.aar)
      if ! valid_archive "$archive" "$path"; then
        failed=1
        continue
      fi
      if ! unzip -p "$archive" classes.jar >"$WORK/classes.jar" 2>"$WORK/unzip.log"; then
        echo "ERROR: $path: missing or unreadable classes.jar" >&2
        failed=1
        continue
      fi
      if ! scan_jar "$WORK/classes.jar" "$path!classes.jar"; then failed=1; fi
      ;;
    *)
      echo "ERROR: $path: expected a .jar or .aar archive" >&2
      failed=1
      ;;
  esac
done
exit "$failed"
