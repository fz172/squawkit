package dev.fanfly.wingslog.feature.sync.data

/**
 * Something the user needs told, that isn't an error they can act on. Separate from [SyncFailure]:
 * nothing is broken and there is nothing to retry — this reports a thing that already happened.
 */
sealed interface SyncNotice {

  /**
   * Edits made to a shared aircraft while offline could not be saved, because access was revoked
   * before they synced. The rows are gone.
   *
   * This is the one data-loss window in the sharing design (PRD D3), and it is genuinely
   * unavoidable: the writes were only ever valid inside a share the user is no longer in, and the
   * rules will not take them. What is *not* acceptable is destroying someone's work in silence, so
   * the purge reports what it dropped.
   */
  data class ChangesDiscarded(
    /** Tail number if we still know it, else a generic label — the aircraft is being purged. */
    val aircraftLabel: String,
    val discardedCount: Int,
  ) : SyncNotice
}
