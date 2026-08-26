package dev.fanfly.wingslog.feature.ads.model

/**
 * One rendered row of a record list: either a record, or a slot where an ad *may* appear.
 *
 * "May" is the whole point. A slot's **position** is a pure function of the rendered item count
 * ([withAdSlots]); whether it ever holds a creative is decided later by the session cap and the
 * network. At the shipped cadence of 10 with a cap of 5, a 100-item list produces ten slots of which
 * at most five fill — so code that treats a slot as an impression will over-count by a factor of two.
 */
sealed interface ListRow<out T> {

  /** A record — a squawk, maintenance task, log entry, or a group header in a grouped list. */
  data class Item<T>(val value: T) : ListRow<T>

  /**
   * A place an ad may render. [slotIndex] is 0-based and **stable for a given list size**, which
   * matters on the one surface that is lazy: the logs `LazyColumn` uses it as the item key, and an
   * identity that changed as items loaded in would make Compose tear the slot down and re-request,
   * burning session-cap headroom on an ad nobody saw.
   */
  data class Ad(val slotIndex: Int) : ListRow<Nothing>
}
