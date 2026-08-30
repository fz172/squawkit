#!/usr/bin/env bash
# Rejects `analytics.logEvent("name", mapOf(...))` — emit a taxonomy type instead:
# `analytics.log(ThingCreated(templateId = ..., source = ...))`.
#
# A GA4 event or parameter name cannot be changed once data has landed against it, and a typo in one
# produces no compile error, no runtime error and no event — the data is simply absent when someone
# builds the report, with no way to backfill. The taxonomy in core/analytics turns each of those
# strings into a type, so the compiler catches what GA4 never will.
#
# The interface method `logEvent` itself stays: it is the boundary the typed `log(event)` extension
# and the three platform actuals are built on. Only *callers* outside core/analytics are rejected.
#
# Reads the PostToolUse payload on stdin; exits 2 to surface the error back to Claude.
set -uo pipefail

file=$(jq -r '.tool_input.file_path // .tool_response.filePath // empty')
[ -n "$file" ] && [ -f "$file" ] || exit 0

case "$file" in
  *.kt) ;;
  *) exit 0 ;;
esac

# core/analytics defines and implements the hatch; webApp's BrowserTitleAnalytics is a decorator
# that forwards it. Neither is a taxonomy call site.
case "$file" in
  */core/analytics/*) exit 0 ;;
  */BrowserTitleAnalytics.kt) exit 0 ;;
esac

# A logEvent call reached through a receiver — `analytics.logEvent(`, `telemetry.logEvent(` — as
# opposed to the interface declaration or an override of it.
hits=$(grep -nE '[[:alnum:]_]\.logEvent[[:space:]]*\(' "$file" || true)

if [ -n "$hits" ]; then
  echo "Use the typed taxonomy: analytics.log(SomeEvent(...)), not analytics.logEvent(\"name\", map)" >&2
  echo "Add the event to core/analytics/AnalyticsEvents.kt if it does not exist yet." >&2
  echo "$hits" >&2
  exit 2
fi
