#!/usr/bin/env bash
# Rejects Material/ui popups that do not reset the text-selection scope at their boundary.
#
# The web host wraps the app in a SelectionContainer. A popup (dialog, sheet, menu) draws in its
# own layout root, and any text inside it that inherits that scope crashes foundation on
# mouse-down ("layouts are not part of the same hierarchy"). The shadowed popups in
# core/ui/common/compose/SelectionSafePopups.kt reset the scope; use those, and register nav
# dialog destinations with selectionDialog (feature/shell/SelectionDialog.kt), not dialog(). A
# popup with no shadow (Dialog, Popup, ExposedDropdownMenu) must wrap its content in
# TextSelectionLayer or DisableSelection itself — the file is accepted when it names either.
#
# Reads the PostToolUse payload on stdin; exits 2 to surface the error back to Claude.
set -uo pipefail

file=$(jq -r '.tool_input.file_path // .tool_response.filePath // empty')
[ -n "$file" ] && [ -f "$file" ] || exit 0

case "$file" in
  *SelectionSafePopups.kt | *SelectionDialog.kt) exit 0 ;;
  *.kt) ;;
  *) exit 0 ;;
esac

hits=$(grep -nE '^import androidx\.compose\.material3\.(AlertDialog|ModalBottomSheet|DropdownMenu|DatePickerDialog)$' "$file" || true)
if [ -n "$hits" ]; then
  echo "Import this popup from dev.fanfly.wingslog.core.ui.common.compose instead; it resets the text-selection scope at the popup boundary" >&2
  echo "$hits" >&2
  exit 2
fi

hits=$(grep -nE '(^|[^A-Za-z_.])dialog\(' "$file" || true)
if [ -n "$hits" ]; then
  echo "Register nav dialog destinations with selectionDialog(...) (feature/shell/SelectionDialog.kt), not dialog(...)" >&2
  echo "$hits" >&2
  exit 2
fi

if grep -qE '^import androidx\.compose\.ui\.window\.(Dialog|Popup)$|\bExposedDropdownMenu\(' "$file" \
  && ! grep -qE '\b(TextSelectionLayer|DisableSelection)\b' "$file"; then
  echo "A raw Dialog/Popup/ExposedDropdownMenu must wrap its content in TextSelectionLayer or DisableSelection (see SelectionSafePopups.kt)" >&2
  exit 2
fi
