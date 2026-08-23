package dev.fanfly.wingslog.feature.notifications.engine

/**
 * Web has no background scheduler (design §5.4, §6.6). A service worker could fake one, but only
 * while the browser keeps it alive, and web N2 is deliberately session-scan-only — the foreground
 * scan is the only scan, and only while a tab is open.
 *
 * A no-op binding rather than no binding at all: [UrgencyScanScheduler] is `expect`-provided on
 * every platform, so common code can depend on it without asking which host it is running on.
 */
class NoOpUrgencyScanScheduler : UrgencyScanScheduler {
  override fun ensureScheduled() = Unit
  override fun cancel() = Unit
}
