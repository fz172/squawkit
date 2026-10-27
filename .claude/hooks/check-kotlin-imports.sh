#!/usr/bin/env bash
# Enforces Android Studio / .editorconfig import layout on edited Kotlin files.
# Shared with the Gradle task checkKotlinImports (under `lint`, so CI too).
#
# Reads the PostToolUse payload on stdin; exits 2 to surface the error back to Claude.
set -uo pipefail

payload=$(cat)
file=$(jq -r '.tool_input.file_path // .tool_response.filePath // empty' <<<"$payload")
[ -n "$file" ] && [ -f "$file" ] || exit 0

case "$file" in
  *.kt | *.kts) ;;
  *) exit 0 ;;
esac

"$(dirname "$0")/../../scripts/check-kotlin-imports.sh" "$file" || exit 2
