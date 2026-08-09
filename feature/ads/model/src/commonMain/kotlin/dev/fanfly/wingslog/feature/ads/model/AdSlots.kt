package dev.fanfly.wingslog.feature.ads.model

/** One ad slot per this many rendered records (PRD §6.2, D1). */
const val DEFAULT_AD_INTERVAL: Int = 10

/**
 * Interleaves ad slots into a flat list of records, per PRD §6.2.
 *
 * - `n == 0` → **no ads**. Empty for *any* reason — no records, filtered to nothing, an error, or
 *   the loading state before first emission — produces no slot at all (G3). The ad count is a
 *   function of the rendered item count, and `f(0) = 0`.
 * - `0 < n < interval` → exactly **one** ad, after the final record.
 * - `n >= interval` → an ad after every [interval]-th record, and **no trailing ad for the
 *   remainder**. `n = 25` yields slots after records 10 and 20; the last five get nothing.
 *
 * The short-list rule exists so a small logbook still shows *an* ad. It is deliberately not applied
 * to trailing remainders, which would double ad density on long lists for no revenue worth the
 * annoyance.
 *
 * Callers wrap only when ads are enabled; when disabled they render their items directly, so the
 * ad-free path allocates nothing.
 */
fun <T> withAdSlots(
  items: List<T>,
  interval: Int = DEFAULT_AD_INTERVAL,
): List<ListRow<T>> = buildAdRows(items, interval) { false }

/**
 * The same cadence over a list that already has group headers flattened into it — the shape the
 * maintenance-tasks Compliance section renders, where status groups share one continuous scroll.
 *
 * Two rules come out of this rather than needing a repair pass afterwards, which is worth stating
 * because the design doc sketched a `coalesceAcrossHeaders()` step that turns out to be unnecessary:
 *
 * - **Headers never count toward the cadence.** [isHeader] entries pass through as
 *   [ListRow.Item] but do not advance the counter, so `n` is the number of *records* across all
 *   groups — one continuous count, not a per-group one.
 * - **A slot can never separate a header from its first card** (G1). Ads are only ever emitted
 *   *after* a record, so `[header, ad, …]` is unconstructible. When a slot falls on a group
 *   boundary it lands as `[…lastRecordOfGroupA, ad, headerOfGroupB…]` — the end of the preceding
 *   group, exactly as PRD §6.5 requires. G2 (never the first card) holds for the same reason.
 *
 * The single short-list ad goes after the **last record** rather than at the absolute end, so a
 * trailing empty group cannot strand an ad below a header with no cards under it.
 */
fun <T> withAdSlotsGrouped(
  entries: List<T>,
  interval: Int = DEFAULT_AD_INTERVAL,
  isHeader: (T) -> Boolean,
): List<ListRow<T>> = buildAdRows(entries, interval, isHeader)

private fun <T> buildAdRows(
  entries: List<T>,
  interval: Int,
  isHeader: (T) -> Boolean,
): List<ListRow<T>> {
  require(interval > 0) { "Ad interval must be positive, was $interval" }

  val recordCount = entries.count { !isHeader(it) }
  // f(0) = 0. Headers with no records under them still render; they just carry no ad.
  if (recordCount == 0) return entries.map { ListRow.Item(it) }

  val rows = ArrayList<ListRow<T>>(entries.size + recordCount / interval + 1)

  if (recordCount < interval) {
    var remaining = recordCount
    entries.forEach { entry ->
      rows += ListRow.Item(entry)
      if (isHeader(entry)) return@forEach
      remaining--
      if (remaining == 0) rows += ListRow.Ad(slotIndex = 0)
    }
    return rows
  }

  var recordsSeen = 0
  var nextSlot = 0
  entries.forEach { entry ->
    rows += ListRow.Item(entry)
    if (isHeader(entry)) return@forEach
    recordsSeen++
    if (recordsSeen % interval == 0) rows += ListRow.Ad(slotIndex = nextSlot++)
  }
  return rows
}
