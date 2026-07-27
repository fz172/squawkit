package dev.fanfly.wingslog.feature.subscription.datamanager

/**
 * Asks the server to re-check this account's entitlement against the billing provider.
 *
 * The escape hatch for the one case the entitlement pipeline cannot self-heal quickly: the store
 * took payment, but the webhook that turns that into an entitlement never arrived. The account holds
 * no Pro entitlement, so the daily reconciler has nothing stale to notice — and the pilot is looking
 * at "Activating SquawkIt Pro…" right now.
 *
 * This does **not** grant anything. It carries no payload at all: the server takes the uid from the
 * caller's auth token, asks RevenueCat itself, and writes through the same server-authoritative
 * path. The client is asking a question, not asserting an answer.
 */
interface EntitlementReconciler {

  /**
   * Requests a re-check. Returns true when the server actually corrected the entitlement.
   *
   * Never throws: a failed reconcile must not surface as an error on top of a purchase that already
   * succeeded. A `false` return means "nothing changed", which is also what a throttled, offline or
   * failed call looks like — the caller should treat this as a hint, not a result, and keep waiting
   * on the synced entitlement either way.
   */
  suspend fun reconcileNow(): Boolean
}

/** Used where there is no backend to ask — tests, and any host without the callable wired. */
object NoOpEntitlementReconciler : EntitlementReconciler {
  override suspend fun reconcileNow(): Boolean = false
}
