package dev.fanfly.wingslog.feature.notifications.engine

/**
 * Runs [UrgencyScanner.scan] with [ScanTrigger.SCHEDULED] on the platform's background scheduler
 * (design §5.4). Android uses a `PeriodicWorkRequest`, iOS a `BGAppRefreshTask`, web nothing at all.
 *
 * The target cadence is **at least every 2h** (design §6.6, superseding the earlier once-daily
 * target): the scan is the only detection mechanism N2 has, so a tighter cadence is a direct
 * latency win rather than a backstop. Only Android gets close to it — iOS's app-refresh tasks are
 * entirely OS-scheduled and `earliestBeginDate` is a lower bound the system freely blows past — so
 * on both platforms the session-boundary scan (P2.8) is what actually carries an active user.
 *
 * Scheduling is unconditional: [ensureScheduled] does not consult notification preferences or OS
 * permission, because [UrgencyScanner.scan] already early-exits on both. That is what makes the
 * design's "call it after every permission/preference change" unnecessary in practice — there is no
 * state a change could invalidate, so calling it once per launch is enough.
 */
interface UrgencyScanScheduler {
  /** Idempotent. Called when the platform Koin module is built, i.e. once per app launch. */
  fun ensureScheduled()

  /** Drops the pending background scan. Nothing calls this yet; it exists so cancellation has a home. */
  fun cancel()
}
