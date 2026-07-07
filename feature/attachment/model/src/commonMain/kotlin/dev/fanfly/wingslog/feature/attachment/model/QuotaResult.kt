package dev.fanfly.wingslog.feature.attachment.model

/**
 * Outcome of `QuotaChecker.check`. See docs/storage/storage_r2_design.md §9b. The picker calls this
 * synchronously before adding a candidate file to the form's pending list; on rejection it
 * surfaces the corresponding inline error string from `feature/attachment/sharedassets`.
 */
sealed class QuotaResult {
  data object Allowed : QuotaResult()

  /** A non-LINK attachment with the same sha256 is already on this parent. */
  data class DuplicateOnParent(val sha256: String) : QuotaResult()

  /** Adding this file would push the parent's total beyond the per-parent cap ([capBytes]). */
  data class PerParentExceeded(val capBytes: Long, val wouldBeBytes: Long) :
    QuotaResult()

  /** Adding this file would push the user's total beyond the 1 GB cap. */
  data class PerUserExceeded(
    val capBytes: Long,
    val usedBytes: Long,
    val candidateBytes: Long,
  ) : QuotaResult()
}
