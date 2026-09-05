#!/usr/bin/env bash
# Rejects popups that do not reset the text-selection scope at their boundary.
#
# The web host wraps the app in a SelectionContainer. A popup (dialog, sheet, menu, tooltip)
# draws in its own layout root, and any text inside it that inherits that scope crashes
# foundation on mouse-down ("layouts are not part of the same hierarchy"). There is no runtime
# catch for it, so the rules are static — see AGENTS.md § Popups start their own text-selection
# scope:
#
#   1. AlertDialog / ModalBottomSheet / DropdownMenu / DatePickerDialog come from
#      core/ui/common/compose/SelectionSafePopups.kt, never from Material directly. A module that
#      cannot depend on core/ui imports the Material one under an alias (`as M3DropdownMenu`) and
#      then falls under rule 3.
#   2. Nav dialog destinations use selectionDialog (feature/shell), never dialog(...).
#   3. Every other popup-creating API — ui.window.Dialog / Popup, BasicAlertDialog,
#      ExposedDropdownMenu, tooltips, expanded search bars, the modal wide rail — is only allowed in
#      a file that also names TextSelectionLayer or DisableSelection.
#   4. No wildcard import of material3, foundation or ui.window: it would hide any of the above.
#
# Usage: check-popup-selection-scopes.sh [file.kt ...]   (no args: every tracked .kt file)
# Run by the Gradle task checkPopupSelectionScopes (under `lint`, so CI too) and by the
# .claude/hooks/no-raw-popups.sh hook on each edit. Exit 1 on any violation.
set -uo pipefail

cd "$(dirname "$0")/.." || exit 1

SHADOWED='AlertDialog|ModalBottomSheet|DropdownMenu|DatePickerDialog'
NEEDS_WRAPPER='BasicAlertDialog|ExposedDropdownMenu|TooltipBox|PlainTooltip|RichTooltip|BasicTooltipBox|ExpandedFullScreenSearchBar|ExpandedDockedSearchBar|ModalWideNavigationRail|TimePickerDialog'
# ui.window imports that are plain types, not popup composables.
WINDOW_TYPES='DialogProperties|PopupProperties|PopupPositionProvider|SecureFlagPolicy|ComposeViewport|ComposeUIViewController'
WRAPPER_MARK='\b(TextSelectionLayer|DisableSelection)\b'

files=()
if [ "$#" -gt 0 ]; then
  files=("$@")
else
  # bash 3 (macOS) has no mapfile.
  while IFS= read -r f; do files+=("$f"); done < <(git ls-files -- '*.kt' 2>/dev/null || find . -name '*.kt' -not -path '*/build/*')
fi

status=0
fail() {
  echo "$1: $2" >&2
  [ -n "${3:-}" ] && echo "$3" >&2
  status=1
}

for file in "${files[@]}"; do
  [ -f "$file" ] || continue
  case "$file" in
    *.kt) ;;
    *) continue ;;
  esac
  case "$file" in
    */SelectionSafePopups.kt | */SelectionDialog.kt | */TextSelection.kt) continue ;;
  esac

  hits=$(grep -nE "^import androidx\.compose\.material3\.($SHADOWED)\$|androidx\.compose\.material3\.($SHADOWED)\(" "$file" || true)
  [ -n "$hits" ] && fail "$file" "import this popup from dev.fanfly.wingslog.core.ui.common.compose; it resets the text-selection scope at the popup boundary" "$hits"

  hits=$(grep -nE '^import androidx\.compose\.(material3|foundation|ui\.window)\.\*$' "$file" || true)
  [ -n "$hits" ] && fail "$file" "no wildcard import here: it can hide a popup composable from this check" "$hits"

  hits=$(grep -nE '(^|[^A-Za-z_.])dialog\(' "$file" || true)
  [ -n "$hits" ] && fail "$file" "register nav dialog destinations with selectionDialog(...) (feature/shell/SelectionDialog.kt), not dialog(...)" "$hits"

  raw=$(grep -nE "\b($NEEDS_WRAPPER)\(|^import androidx\.compose\.material3\.($SHADOWED) as " "$file" || true)
  window=$(grep -nE '^import androidx\.compose\.ui\.window\.[A-Za-z]+$' "$file" | grep -vE "\.($WINDOW_TYPES)\$" || true)
  if [ -n "$raw$window" ] && ! grep -qE "$WRAPPER_MARK" "$file"; then
    fail "$file" "this popup must wrap its content in TextSelectionLayer or DisableSelection (see SelectionSafePopups.kt)" "$raw${raw:+$'\n'}$window"
  fi
done

exit $status
