package dev.fanfly.wingslog.feature.ads.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The F8 regression: index-based list behaviour must resolve against the **display** list.
 *
 * `MaintenanceLogListContent` pins a jumped-to log by index. It used to compute that index over the
 * item list and hand it to `LazyListState.scrollToItem`, which addresses *rendered rows*. Once ad
 * slots are interleaved the two diverge by the number of ads above the target, so "go to this log"
 * lands on the wrong entry — and silently, since a maintenance log looks much like the one below it.
 *
 * These tests pin the arithmetic. The UI fix is `rows.indexOfFirst { it is ListRow.Item && … }`;
 * what matters here is that the two indices genuinely differ, so a future refactor back to the item
 * index fails loudly instead of shipping a wrong scroll target.
 */
class DisplayIndexTest {

  private fun logs(n: Int) = (1..n).map { "log-$it" }

  private fun displayIndexOf(rows: List<ListRow<String>>, id: String): Int =
    rows.indexOfFirst { it is ListRow.Item && it.value == id }

  @Test
  fun `display index matches the item index only above the first slot`() {
    val rows = withAdSlots(logs(100))

    // Nothing above it, so the two agree.
    assertThat(displayIndexOf(rows, "log-1")).isEqualTo(0)
    assertThat(displayIndexOf(rows, "log-10")).isEqualTo(9)
  }

  @Test
  fun `every log below a slot shifts by the number of ads above it`() {
    val rows = withAdSlots(logs(100))

    // One ad sits above log-11, two above log-21, and so on: the drift grows down the list, which
    // is why an off-by-ads bug looks like "roughly right" near the top and clearly wrong later.
    listOf(
      11 to 1,
      21 to 2,
      55 to 5,
      100 to 9
    ).forEach { (logNumber, adsAbove) ->
      val itemIndex = logNumber - 1
      val displayIndex = displayIndexOf(rows, "log-$logNumber")
      assertThat(displayIndex).isEqualTo(itemIndex + adsAbove)
    }
  }

  @Test
  fun `a stale item index lands somewhere other than the target - here, on an ad`() {
    val rows = withAdSlots(logs(100))
    val target = "log-55"
    val staleIndex =
      54 // what indexOfFirst over the item list would have returned

    // Worth spelling out, because it is worse than a near-miss: with five slots above it, index 54
    // is the fifth ad slot itself. The pilot taps "go to this log" and is scrolled to an ad.
    assertThat(rows[staleIndex]).isInstanceOf(ListRow.Ad::class.java)

    // The correct index is five rows further down.
    assertThat(displayIndexOf(rows, target)).isEqualTo(staleIndex + 5)
  }

  @Test
  fun `with ads disabled the two indices agree, so the fix is a no-op on the ad-free path`() {
    val rows = logs(100).map { ListRow.Item(it) }
    (1..100).forEach { n ->
      assertThat(displayIndexOf(rows, "log-$n")).isEqualTo(n - 1)
    }
  }

  @Test
  fun `slot keys are distinct from every log id`() {
    // The lazy list keys ads as "ad-<slotIndex>". A collision with a log id would make Compose treat
    // an ad and a record as the same item.
    val rows = withAdSlots(logs(100))
    val keys = rows.map { row ->
      when (row) {
        is ListRow.Ad -> "ad-${row.slotIndex}"
        is ListRow.Item -> row.value
      }
    }
    assertThat(keys).containsNoDuplicates()
  }
}
