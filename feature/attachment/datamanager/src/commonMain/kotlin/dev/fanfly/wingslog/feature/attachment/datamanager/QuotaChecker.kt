package dev.fanfly.wingslog.feature.attachment.datamanager

import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.feature.attachment.model.QuotaResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext

/**
 * Enforces the three attachment caps from docs/storage/storage_r2_design.md §9b:
 *
 * | Check | Value |
 * |---|---|
 * | Per-parent duplicate (sha256) | reject if already on this parent |
 * | Per-parent size | 25 MB summed across non-LINK attachments on that parent |
 * | Per-user size | 1 GB summed across the user's scope (incl. REMOTE_ONLY) |
 *
 * The picker calls [check] *after* it has read the candidate bytes and computed sha256 (the
 * same hash is then handed to [LocalBlobStore.put] so it isn't recomputed).
 */
class QuotaChecker(
  private val db: WingsLogDatabase,
  private val ioContext: CoroutineContext,
  private val perParentCapBytes: Long = PARENT_CAP_BYTES,
  private val perUserCapBytes: Long = USER_CAP_BYTES,
) {

  data class State(
    val perUserUsedBytes: Long,
    val perUserCapBytes: Long,
  ) {
    val perUserRemaining: Long
      get() = (perUserCapBytes - perUserUsedBytes).coerceAtLeast(
        0
      )
  }

  /** Reactive view of per-user usage for the Settings → Storage progress bar. */
  fun observeState(scope: EntityScope): Flow<State> =
    db.schemaQueries.sumBlobSizeInScope(scope.toPath())
      .asFlow()
      .mapToOne(ioContext)
      .map { used ->
        State(
          perUserUsedBytes = used,
          perUserCapBytes = perUserCapBytes
        )
      }

  /**
   * Check whether [candidateBytes]-sized file with [candidateSha256] would be admissible on a
   * parent that already holds [parentNonLinkSha256s] / [pendingBytesOnParent] of attachments.
   *
   * @param candidateSha256       hex sha256 of the candidate bytes (already computed by picker)
   * @param candidateBytes        size of the candidate file in bytes
   * @param parentNonLinkSha256s  sha256s of non-LINK attachments already on the parent
   *                              (`Local` + `Saved`, excluding `PendingDelete`)
   * @param pendingBytesOnParent  sum of size_bytes of those same attachments
   * @param scope                 the user's scope for the per-user cap
   */
  suspend fun check(
    candidateSha256: String,
    candidateBytes: Long,
    parentNonLinkSha256s: Set<String>,
    pendingBytesOnParent: Long,
    scope: EntityScope,
  ): QuotaResult {
    // 1. Cheapest first — duplicate on parent.
    if (candidateSha256 in parentNonLinkSha256s) {
      return QuotaResult.DuplicateOnParent(candidateSha256)
    }
    // 2. Per-parent size.
    val wouldBe = pendingBytesOnParent + candidateBytes
    if (wouldBe > perParentCapBytes) {
      return QuotaResult.PerParentExceeded(
        capBytes = perParentCapBytes,
        wouldBeBytes = wouldBe
      )
    }
    // 3. Per-user size — single SQL aggregate.
    val used = db.schemaQueries.sumBlobSizeInScope(scope.toPath())
      .awaitAsOne()
    if (used + candidateBytes > perUserCapBytes) {
      return QuotaResult.PerUserExceeded(
        capBytes = perUserCapBytes,
        usedBytes = used,
        candidateBytes = candidateBytes,
      )
    }
    return QuotaResult.Allowed
  }

  companion object {
    const val PARENT_CAP_BYTES: Long = 25L * 1024 * 1024  // 25 MB
    const val USER_CAP_BYTES: Long = 1024L * 1024 * 1024  // 1 GB
  }
}
