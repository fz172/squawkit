#!/usr/bin/env bash
# Rejects maps and tuples keyed or valued by a primitive — `Map<DataLogId, String>`,
# `Map<String, Thing>`, `Pair<Int, Foo>`, `Triple<…, Long>` — in newly written lines. Name the
# values with a data class and pass a List of it:
#   data class PickedDataLog(val id: DataLogId, val displayName: String)
#
# A bare String beside an id says nothing about what it holds (name? title? URL?), and positional
# pairs make two values of the same type trivially swappable.
#
# Only lines this edit adds are checked, so the existing uses stay until someone touches them. A
# genuine boundary (Firebase analytics params, a wire-name lookup table) opts out on the same line
# with `// allow-primitive-tuple: <reason>`. Test source sets are exempt.
#
# Reads the PostToolUse payload on stdin; exits 2 to surface the error back to Claude.
set -uo pipefail

payload=$(cat)
file=$(jq -r '.tool_input.file_path // .tool_response.filePath // empty' <<<"$payload")
[ -n "$file" ] && [ -f "$file" ] || exit 0

case "$file" in
  *.kt) ;;
  *) exit 0 ;;
esac
case "$file" in
  */build/* | */generated/* | */src/test/* | */src/*Test/*) exit 0 ;;
esac

# Write replaces the whole file, so compare it against HEAD; Edit carries its own before/after.
head_content=""
if [ "$(jq -r '.tool_name' <<<"$payload")" = "Write" ]; then
  dir=$(dirname "$file")
  if root=$(git -C "$dir" rev-parse --show-toplevel 2>/dev/null); then
    rel=${file#"$root"/}
    head_content=$(git -C "$root" show "HEAD:$rel" 2>/dev/null || true)
  fi
fi

PAYLOAD="$payload" HEAD_CONTENT="$head_content" python3 - "$file" <<'PY'
import json, os, re, sys
from collections import Counter

payload = json.loads(os.environ["PAYLOAD"])
path = sys.argv[1]
tool = payload.get("tool_name")
inp = payload.get("tool_input") or {}

if tool == "Write":
    before = os.environ.get("HEAD_CONTENT", "")
    after = inp.get("content")
    if after is None:
        after = open(path, encoding="utf-8").read()
elif tool == "Edit":
    before = inp.get("old_string", "")
    after = inp.get("new_string", "")
else:
    sys.exit(0)

PRIMITIVES = {
    "String", "CharSequence", "Int", "Long", "Short", "Byte", "Double", "Float", "Boolean", "Char",
    "UInt", "ULong", "UShort", "UByte", "Number",
}
OPENER = re.compile(
    r"(?<![\w.])(?:(?:Mutable)?Map|HashMap|LinkedHashMap|SortedMap|Pair|Triple"
    r"|mapOf|mutableMapOf|hashMapOf|linkedMapOf|sortedMapOf|emptyMap)<"
)


def type_args(line, start):
    """Top-level comma-separated arguments of the generic list opening at line[start] == '<'."""
    depth, args, cur = 0, [], ""
    for i, ch in enumerate(line[start:], start):
        if ch == ">" and line[i - 1] == "-":  # a function type's arrow
            pass
        elif ch == "<":
            depth += 1
            if depth == 1:
                continue
        elif ch == ">":
            depth -= 1
            if depth == 0:
                args.append(cur)
                return args
        elif ch == "," and depth == 1:
            args.append(cur)
            cur = ""
            continue
        cur += ch
    return None  # generic list continues past this line


def offending(line):
    code = line.split("//", 1)[0]
    found = []
    for m in OPENER.finditer(code):
        args = type_args(code, m.end() - 1)
        if not args or len(args) < 2:
            continue
        names = [re.sub(r"^(?:out|in)\s+|^kotlin\.|\?$", "", a.strip()) for a in args]
        if any(n in PRIMITIVES for n in names):
            found.append(code[m.start():m.end() - 1] + "<" + ", ".join(a.strip() for a in args) + ">")
    return found


added = Counter(after.splitlines()) - Counter(before.splitlines())
hits = []
for line, _ in added.items():
    stripped = line.strip()
    if stripped.startswith(("*", "/*", "//")) or "allow-primitive-tuple" in line:
        continue
    for t in offending(line):
        hits.append((stripped, t))

if not hits:
    sys.exit(0)

file_lines = open(path, encoding="utf-8").read().splitlines()
print("Do not key or value a Map/Pair/Triple by a primitive; name the values with a data class",
      file=sys.stderr)
print("and pass a List of it, e.g. data class PickedDataLog(val id: DataLogId, val displayName: String).",
      file=sys.stderr)
print("A genuine platform boundary may opt out on the line: // allow-primitive-tuple: <reason>",
      file=sys.stderr)
for stripped, t in hits:
    nums = [str(i + 1) for i, l in enumerate(file_lines) if l.strip() == stripped]
    print(f"{path}:{','.join(nums) or '?'}: {t}", file=sys.stderr)
sys.exit(2)
PY
