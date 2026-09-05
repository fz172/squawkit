#!/usr/bin/env bash
# Runs scripts/check-popup-selection-scopes.sh on the edited file — the rules live there, shared
# with the Gradle task checkPopupSelectionScopes (under `lint`, so CI too).
#
# Reads the PostToolUse payload on stdin; exits 2 to surface the error back to Claude.
set -uo pipefail

file=$(jq -r '.tool_input.file_path // .tool_response.filePath // empty')
[ -n "$file" ] && [ -f "$file" ] || exit 0

case "$file" in
  *.kt) ;;
  *) exit 0 ;;
esac

"$(dirname "$0")/../../scripts/check-popup-selection-scopes.sh" "$file" || exit 2
