package dev.fanfly.wingslog.feature.ads.model

/**
 * Identifies one ad slot for the life of a session: which list it is in, and which slot of that list.
 *
 * Slots must be identified independently of composition. A slot scrolled out of the lazy logs list,
 * or on a tab the pilot navigates away from, is *disposed* — and when it comes back it is a fresh
 * composable with no memory of what it was granted. Keying the grant on this rather than on
 * `remember` is what makes an ad still be there when a pilot scrolls back to it, and what stops
 * scroll churn from spending the session budget twice on the same slot (N8).
 */
data class AdSlotKey(
  val surface: AdSurface,
  val slotIndex: Int,
)
