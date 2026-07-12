#!/usr/bin/env bash
# Rejects backslash escape sequences in Kotlin sources and string resources.
#
# `\'` is never needed: not in a Kotlin string ("it's" is already legal), and not in a Compose
# strings.xml entry either — use a typographic apostrophe (’) instead, which is also better
# typography. A Kotlin char literal ('\'') genuinely does need the escape, so it is exempt.
#
# Reads the PostToolUse payload on stdin; exits 2 to surface the error back to Claude.
set -uo pipefail

file=$(jq -r '.tool_input.file_path // .tool_response.filePath // empty')
[ -n "$file" ] && [ -f "$file" ] || exit 0

case "$file" in
  *.kt | *.kts | *.xml) ;;
  *) exit 0 ;;
esac

# Kotlin char literals ('\'', '\\', 'a') legitimately contain backslashes, and '\\' even contains
# a \' substring. Blank them out first, then look for what's left: a \' inside a real string.
hits=$(sed -E "s/'(\\\\.|[^'\\\\])'//g" "$file" | grep -nF "\\'" || true)

if [ -n "$hits" ]; then
  echo "No escape characters in kotlin string" >&2
  echo "$hits" >&2
  echo "Use a typographic apostrophe (’) instead of \\'." >&2
  exit 2
fi
