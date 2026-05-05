package dev.fanfly.wingslog.feature.sync.data

/**
 * Surfaced via [SyncEngine.hydrationState] so the UI can show "Restoring 3 of 6 collections…" on
 * first launch. `total` grows as nested scopes (per-aircraft sub-collections) come into view, so
 * the count can climb during the run — that's intentional.
 */
sealed interface HydrationState {
  /** No hydration in flight — either signed out, anonymous, or every cursor was already hydrated. */
  data object Idle : HydrationState

  data class InProgress(
    val completed: Int,
    val total: Int,
  ) : HydrationState {
    init {
      require(total > 0) { "InProgress requires total > 0" }
      require(completed in 0..total) { "completed=$completed must be in 0..$total" }
    }
  }

  /** All in-flight scopes finished. Stays here until sign-out clears the counters. */
  data object Done : HydrationState
}
