package dev.fanfly.wingslog.feature.sync.logging

import dev.fanfly.wingslog.core.analytics.AnalyticsManager

/**
 * The sharing safety valve from the PRD (§9 Success Metrics): denied writes should sit at ~zero.
 *
 * A denial outside a revocation race means the UI let someone attempt a write their role forbids — a
 * gating bug, not a user error — and it is now *invisible* without a counter, because the sync
 * engine's `PushWorker` swallows the denial by design. That is exactly why it has to be counted.
 *
 * [sharedScopeReconciled] is what makes the denial count readable: a denial paired with a reconcile
 * is the expected §5.4 race, and a denial without one is the bug. Only the two race triggers are
 * counted — the happy path (the revoke function's ref tombstone arrives and the janitor purges) is
 * not, because a race-triggered reconcile deletes the ref itself and so re-enters the janitor,
 * which would double-count it as a tombstone.
 */
interface SyncTelemetry {

  /**
   * A push was rejected by the rules. [sharedScope] distinguishes a write into a host's tree (the
   * revocation race, expected at low volume) from one into the member's own tree (never expected —
   * that is an expired token or a rules regression).
   */
  fun permissionDeniedWrite(sharedScope: Boolean)

  /**
   * The rules told us a share had ended before its ref tombstone did, and we reconciled the member's
   * local copy away. [trigger] says which path noticed — [TRIGGER_DENIED_READ] or
   * [TRIGGER_DENIED_WRITE].
   */
  fun sharedScopeReconciled(trigger: String)

  /** Used by tests and by hosts that wire no analytics. */
  object NoOp : SyncTelemetry {
    override fun permissionDeniedWrite(sharedScope: Boolean) = Unit
    override fun sharedScopeReconciled(trigger: String) = Unit
  }

  companion object {
    const val TRIGGER_DENIED_READ: String = "denied_read"
    const val TRIGGER_DENIED_WRITE: String = "denied_write"
  }
}

/** Forwards [SyncTelemetry] to GA4. */
class AnalyticsSyncTelemetry(
  private val analytics: AnalyticsManager,
) : SyncTelemetry {

  override fun permissionDeniedWrite(sharedScope: Boolean) {
    analytics.logEvent(
      EVENT_PERMISSION_DENIED_WRITE,
      mapOf("scope" to if (sharedScope) "shared" else "own"),
    )
  }

  override fun sharedScopeReconciled(trigger: String) {
    analytics.logEvent(EVENT_SHARE_RECONCILED, mapOf("trigger" to trigger))
  }

  private companion object {
    const val EVENT_PERMISSION_DENIED_WRITE = "sync_permission_denied_write"
    const val EVENT_SHARE_RECONCILED = "sync_share_reconciled"
  }
}
