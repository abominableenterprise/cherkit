# Parsing helpers for scripts/test-mac.sh, kept apart so scripts/tests/test-mac-helpers.sh can check
# them on Linux. Sourced, not run. Written for macOS's stock bash 3.2.

# java_major_parse: reads `java -version` output on stdin, prints the major version.
# Handles "17.0.2", "21", "27-ea", "26-internal", "25+36" and the legacy "1.8.0_392" (-> 8).
java_major_parse() {
  sed -n -e 's/.*version "1\.\([0-9][0-9]*\).*/\1/p' -e 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n 1
}

# java_major <java binary>: major version of that JDK, empty if it cannot be read.
java_major() { "$1" -version 2>&1 | java_major_parse; }

# properties_value <file> <key>: the value of <key> in a Java .properties file, decoded the way
# java.util.Properties.load does (continuation lines, `=`/`:`/whitespace separators, `\ `, `\\`,
# `\:`, `\uXXXX`, ...). Prints nothing and succeeds if the file or key is missing; the last
# occurrence of a key wins, as in Java.
properties_value() {
  [ -f "$1" ] || return 0
  python3 - "$1" "$2" <<'PY'
import re
import sys

ESCAPES = {"t": "\t", "n": "\n", "r": "\r", "f": "\f"}


def logical_lines(text):
    pending = None
    for raw in re.split(r"\r\n|\r|\n", text):
        line = raw.lstrip(" \t\f")
        if pending is None and (not line or line[0] in "#!"):
            continue
        trailing = len(line) - len(line.rstrip("\\"))
        joined = line[:-1] if trailing % 2 else line
        pending = joined if pending is None else pending + joined
        if not trailing % 2:
            yield pending
            pending = None
    if pending is not None:
        yield pending


def decode(s):
    out, i = [], 0
    while i < len(s):
        c = s[i]
        if c != "\\" or i + 1 == len(s):
            out.append(c if c != "\\" else "")
            i += 1
            continue
        n = s[i + 1]
        if n == "u" and re.fullmatch(r"[0-9A-Fa-f]{4}", s[i + 2:i + 6]):
            out.append(chr(int(s[i + 2:i + 6], 16)))
            i += 6
            continue
        out.append(ESCAPES.get(n, n))
        i += 2
    return "".join(out)


def split(line):
    i = 0
    while i < len(line) and line[i] not in "=: \t\f":
        i += 2 if line[i] == "\\" else 1
    key, rest = line[:i], line[i:].lstrip(" \t\f")
    if rest[:1] in ("=", ":"):
        rest = rest[1:].lstrip(" \t\f")
    return decode(key), decode(rest)


# Java's Properties.load(InputStream) reads ISO-8859-1; Android Studio writes \uXXXX escapes, but a
# hand-edited file may be UTF-8, so try that first.
with open(sys.argv[1], "rb") as f:
    data = f.read()
try:
    text = data.decode("utf-8")
except UnicodeDecodeError:
    text = data.decode("latin-1")
value = None
for line in logical_lines(text):
    key, val = split(line)
    if key == sys.argv[2]:
        value = val
if value is not None:
    print(value)
PY
}

# pick_android_sdk <local.properties> <default dir>: the first SDK directory (one with platforms/)
# among $ANDROID_HOME, $ANDROID_SDK_ROOT, sdk.dir in <local.properties> and <default dir>.
pick_android_sdk() {
  local candidate
  for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$(properties_value "$1" sdk.dir || true)" "$2"; do
    if [ -n "$candidate" ] && [ -d "$candidate/platforms" ]; then echo "$candidate"; return 0; fi
  done
}

# use_android_sdk <dir>: points both ANDROID_HOME and ANDROID_SDK_ROOT at <dir>, so Gradle never sees
# two different SDK locations.
use_android_sdk() {
  export ANDROID_HOME="$1"
  export ANDROID_SDK_ROOT="$1"
}
