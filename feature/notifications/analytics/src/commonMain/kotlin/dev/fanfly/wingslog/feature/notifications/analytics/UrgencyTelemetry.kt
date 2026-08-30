package dev.fanfly.wingslog.feature.notifications.analytics

import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.UrgencyNotificationPosted
import dev.fanfly.wingslog.core.analytics.log
import dev.fanfly.wingslog.core.storage.CloudSyncSetting
import dev.fanfly.wingslog.feature.notifications.model.ScanTrigger
import dev.gitlive.firebase.auth.FirebaseAuth

/**
 * The two N2 numbers the design asks for (§6.6, §12.3; PRD §11). Not optional telemetry — the first
 * of them decides a product claim.
 *
 * 1. **Which trigger delivered the notification.** The share coming from the background scheduler
 *    versus the session-boundary scan, per platform, is what says whether iOS can honestly claim a
 *    cadence at all or whether §7.5's copy has to become foreground-only. `BGAppRefreshTask` is
 *    opportunistic and no amount of local testing answers this; only the field does.
 * 2. **Whether the fleet has anything shared into it.** N2's reach beyond collaboration is the
 *    argument for having built it before N1, and this is the number that supports or refutes it.
 *
 * Platform is not a parameter — Firebase attaches it to every event already.
 */
interface UrgencyTelemetry {

  /**
   * [count] urgency notifications were posted by one scan.
   *
   * @param sharedFleet at least one thing in the fleet was shared *into* this account. Note it
   *   does not detect an owner who only shares *out* — that needs the sharing datamanager's
   *   outbound view, and the question here is whether N2 reaches people who receive nothing from
   *   collaborators.
   */
  fun urgencyNotificationsPosted(
    trigger: ScanTrigger,
    count: Int,
    sharedFleet: Boolean
  )

  /** Used by tests and by hosts that wire no analytics. */
  object NoOp : UrgencyTelemetry {
    override fun urgencyNotificationsPosted(
      trigger: ScanTrigger,
      count: Int,
      sharedFleet: Boolean,
    ) = Unit
  }
}

/**
 * Forwards [UrgencyTelemetry] to GA4, **and is where the §12.3 privacy gate lives**.
 *
 * N2 is otherwise entirely on-device — local data in, local notification out — so this telemetry is
 * the one thing that would put a scan on the network for a pilot whose fleet has never left the
 * phone. It therefore reports nothing when cloud sync is off or the account is anonymous. That
 * deliberately loses the population whose privacy expectation is strongest, and in exchange the
 * privacy policy's "nothing leaves your device" claim about urgency alerts stays literally true
 * rather than true-with-an-asterisk.
 *
 * The gate is here rather than at the call site on purpose: one choke point that cannot be
 * forgotten by a future caller.
 *
 * One event per notification rather than one per scan carrying a count: [AnalyticsManager.logEvent]
 * takes string params only, so a count would land in GA4 as a dimension and "share by trigger"
 * would mean summing strings. Counting events gives the share directly. Volume is a handful a day
 * at most.
 */
class AnalyticsUrgencyTelemetry(
  private val analytics: AnalyticsManager,
  private val auth: FirebaseAuth,
  private val cloudSync: CloudSyncSetting,
) : UrgencyTelemetry {

  override fun urgencyNotificationsPosted(
    trigger: ScanTrigger,
    count: Int,
    sharedFleet: Boolean,
  ) {
    if (count <= 0 || !reportingAllowed()) return
    val event = UrgencyNotificationPosted(
      trigger = trigger.name.lowercase(),
      sharedFleet = sharedFleet,
    )
    repeat(count) { analytics.log(event) }
  }

  /** §12.3: no report at all for an anonymous or sync-off account. */
  private fun reportingAllowed(): Boolean {
    val user = auth.currentUser ?: return false
    if (user.isAnonymous) return false
    return cloudSync.isCloudSyncEnabled()
  }
}
