package dev.fanfly.wingslog.feature.sync.data

import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityScope

/**
 * Surfaced via [SyncEngine.failureState] for a banner / settings row. R1 keeps this minimal: the
 * UI just needs to know whether something is currently wrong and how to phrase it. Detailed
 * per-scope state lives on the `sync_cursor` table.
 */
sealed interface SyncFailure {
  val message: String

  data class Hydration(
    val kind: CollectionKind,
    val scope: EntityScope,
    val failedAttempts: Int,
    val cause: String?,
  ) : SyncFailure {
    override val message: String =
      "Sync failed for ${kind.wireName} (${failedAttempts}× retries): ${cause ?: "unknown"}"
  }

  /**
   * Auth-class push failure: token expired, permission denied, or unauthenticated. The user must
   * re-sign-in for sync to recover; transparent retries won't help.
   */
  data class AuthExpired(override val message: String = "Authentication expired") :
    SyncFailure

  /**
   * Non-auth, non-transient push failure (validation, malformed doc, server error, etc.).
   * Transient errors (network, deadline, internal) intentionally don't surface here — they're
   * picked up automatically when conditions improve.
   */
  data class Push(override val message: String) : SyncFailure
}

/**
 * `30s × 2^min(failed_attempts, 6)`, capped at 30min. Returns 0 for `failedAttempts <= 0`.
 */
internal fun backoffMs(failedAttempts: Int): Long {
  if (failedAttempts <= 0) return 0L
  val exp = minOf(
    failedAttempts,
    6
  )
  val seconds =
    30L shl exp.coerceAtMost(30) // shl past 30 would overflow; we're capped at 6 anyway
  val cappedSeconds = minOf(
    seconds,
    30L * 60L
  )
  return cappedSeconds * 1000L
}
