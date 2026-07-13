#!/usr/bin/env bash
# Rejects bare `get()` in Koin modules — inject with an explicit type: `get<ClassType>()`.
#
# A bare get() resolves by position and silently binds to whatever type the constructor happens to
# declare there. Reorder the constructor and it still compiles, now wired to the wrong dependency.
# The explicit type argument makes that a compile error instead.
#
# Kotlin property accessors (`val x: Boolean get() = ...`, `get() { ... }`) are a different thing
# entirely and are left alone, as are method calls like `snapshot.get()`.
#
# Reads the PostToolUse payload on stdin; exits 2 to surface the error back to Claude.
set -uo pipefail

file=$(jq -r '.tool_input.file_path // .tool_response.filePath // empty')
[ -n "$file" ] && [ -f "$file" ] || exit 0

case "$file" in
  *.kt | *.kts) ;;
  *) exit 0 ;;
esac

# Only Koin call sites can be wrong here.
grep -q 'org\.koin' "$file" || exit 0

# A get() that is not preceded by a dot and not followed by `=` or `{` — i.e. an injection, not a
# property getter and not a method call.
hits=$(grep -nE '(^|[^.[:alnum:]_])get\(\)[[:space:]]*([,)]|$)' "$file" || true)

if [ -n "$hits" ]; then
  echo "Use get<ClassType>() syntax, not bare get()" >&2
  echo "$hits" >&2
  exit 2
fi
