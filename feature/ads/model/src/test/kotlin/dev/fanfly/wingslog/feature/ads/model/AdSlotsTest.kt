package dev.fanfly.wingslog.feature.ads.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The normative placement tests. The table in [cadenceMatrix] is PRD §6.6 transcribed — if a change
 * makes a row here fail, the change is wrong, not the row.
 */
class AdSlotsTest {

  private fun records(n: Int): List<String> = (1..n).map { "record-$it" }

  /** The 1-based record positions each ad slot sits immediately after. */
  private fun List<ListRow<String>>.adPositions(): List<Int> {
    var seen = 0
    val positions = mutableListOf<Int>()
    forEach { row ->
      when (row) {
        is ListRow.Item -> seen++
        is ListRow.Ad -> positions += seen
      }
    }
    return positions
  }

  private fun List<ListRow<String>>.items(): List<String> =
    filterIsInstance<ListRow.Item<String>>().map { it.value }

  private fun List<ListRow<String>>.adCount(): Int = count { it is ListRow.Ad }

  // ---------------------------------------------------------------- PRD §6.6

  @Test
  fun `cadenceMatrix - PRD section 6_6, slot positions are a function of item count`() {
    // n to the 1-based record positions the slots follow.
    val matrix = mapOf(
      0 to emptyList<Int>(),
      1 to listOf(1),
      7 to listOf(7),
      9 to listOf(9),
      10 to listOf(10),
      11 to listOf(10),
      19 to listOf(10),
      20 to listOf(10, 20),
      25 to listOf(10, 20),
      100 to listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100),
    )

    matrix.forEach { (n, expected) ->
      val rows = withAdSlots(records(n))
      assertThat(rows.adPositions()).isEqualTo(expected)
      // Records always survive intact and in order, whatever the ads do around them.
      assertThat(rows.items()).isEqualTo(records(n))
    }
  }

  @Test
  fun `empty list gets no ad, for any reason`() {
    // G3: no records, filtered to nothing, error, loading - all reach here as an empty list.
    assertThat(withAdSlots(emptyList<String>())).isEmpty()
  }

  @Test
  fun `short list puts its single ad last`() {
    val rows = withAdSlots(records(7))
    assertThat(rows.adCount()).isEqualTo(1)
    assertThat(rows.last()).isEqualTo(ListRow.Ad(0))
  }

  @Test
  fun `at the interval boundary the short-list and cadence rules agree`() {
    // n == 10 produces one ad which is also the last card - rules 2 and 3 must not both fire.
    val rows = withAdSlots(records(10))
    assertThat(rows.adCount()).isEqualTo(1)
    assertThat(rows.last()).isEqualTo(ListRow.Ad(0))
  }

  @Test
  fun `a trailing remainder gets no ad of its own`() {
    // 25 records: slots after 10 and 20, and nothing for the last five.
    val rows = withAdSlots(records(25))
    assertThat(rows.adCount()).isEqualTo(2)
    assertThat(rows.last()).isEqualTo(ListRow.Item("record-25"))
  }

  @Test
  fun `an ad is never the first row`() {
    // G2: the first rendered card is always a record.
    listOf(1, 5, 10, 11, 100).forEach { n ->
      assertThat(withAdSlots(records(n)).first()).isInstanceOf(ListRow.Item::class.java)
    }
  }

  // ------------------------------------------------------------- slot identity

  @Test
  fun `slot indices are zero-based, sequential and stable for a given size`() {
    val rows = withAdSlots(records(100))
    val slots = rows.filterIsInstance<ListRow.Ad>()
      .map { it.slotIndex }
    assertThat(slots).isEqualTo((0..9).toList())
    // Stability is what the logs LazyColumn keys on; recomputing must not renumber.
    assertThat(withAdSlots(records(100)).filterIsInstance<ListRow.Ad>()).isEqualTo(
      rows.filterIsInstance<ListRow.Ad>()
    )
  }

  @Test
  fun `interval is configurable and validated`() {
    assertThat(withAdSlots(records(6), interval = 3).adPositions()).isEqualTo(
      listOf(3, 6)
    )
    assertThat(withAdSlots(records(2), interval = 3).adPositions()).isEqualTo(
      listOf(2)
    )
    runCatching { withAdSlots(records(5), interval = 0) }
      .also { assertThat(it.isFailure).isTrue() }
  }

  // ----------------------------------------------------------------- grouped

  private data class Entry(val label: String, val isHeader: Boolean)

  private fun grouped(vararg groups: Pair<String, Int>): List<Entry> =
    buildList {
      groups.forEach { (name, count) ->
        add(Entry(name, isHeader = true))
        repeat(count) { add(Entry("$name-record-${it + 1}", isHeader = false)) }
      }
    }

  private fun List<ListRow<Entry>>.groupedAdPositions(): List<Int> {
    var seen = 0
    val positions = mutableListOf<Int>()
    forEach { row ->
      when (row) {
        is ListRow.Item -> if (!row.value.isHeader) seen++
        is ListRow.Ad -> positions += seen
      }
    }
    return positions
  }

  @Test
  fun `grouped - the counter runs continuously across groups, headers excluded`() {
    // Three groups of 6 = 18 records. One continuous count means a slot after record 10 overall,
    // which falls inside the second group - not a slot per group, and headers never advance it.
    val rows = withAdSlotsGrouped(
      grouped(
        "overdue" to 6,
        "due-soon" to 6,
        "ok" to 6
      )
    ) { it.isHeader }
    assertThat(rows.groupedAdPositions()).isEqualTo(listOf(10))
  }

  @Test
  fun `grouped - an ad never sits between a header and its first card`() {
    // G1. Every group boundary in a long grouped list, checked structurally.
    val rows = withAdSlotsGrouped(
      grouped(
        "a" to 10,
        "b" to 10,
        "c" to 10
      )
    ) { it.isHeader }
    rows.forEachIndexed { index, row ->
      if (row is ListRow.Item && row.value.isHeader) {
        val next = rows.getOrNull(index + 1)
        assertThat(next).isNotInstanceOf(ListRow.Ad::class.java)
      }
    }
  }

  @Test
  fun `grouped - a boundary slot lands at the end of the preceding group`() {
    // 10 records in group A, then group B: the slot after record 10 must precede B's header.
    val rows = withAdSlotsGrouped(grouped("a" to 10, "b" to 5)) { it.isHeader }
    val adIndex = rows.indexOfFirst { it is ListRow.Ad }
    val before = rows[adIndex - 1] as ListRow.Item
    val after = rows[adIndex + 1] as ListRow.Item
    assertThat(before.value.label).isEqualTo("a-record-10")
    assertThat(after.value.isHeader).isTrue()
  }

  @Test
  fun `grouped - headers with no records produce no ad`() {
    val rows = withAdSlotsGrouped(grouped("empty" to 0)) { it.isHeader }
    assertThat(rows.count { it is ListRow.Ad }).isEqualTo(0)
    assertThat(rows).hasSize(1)
  }

  @Test
  fun `grouped - a short list puts its ad after the last record, not after a trailing header`() {
    val entries =
      grouped("a" to 3) + Entry("trailing-empty-group", isHeader = true)
    val rows = withAdSlotsGrouped(entries) { it.isHeader }
    val adIndex = rows.indexOfFirst { it is ListRow.Ad }
    assertThat((rows[adIndex - 1] as ListRow.Item).value.label).isEqualTo("a-record-3")
    assertThat(rows.last()).isInstanceOf(ListRow.Item::class.java)
  }

  @Test
  fun `toggled sub-views count independently`() {
    // Squawks Open/Closed: each sub-view is its own list with its own counter, which falls out of
    // calling withAdSlots on each filtered list rather than on the union.
    val open = withAdSlots(records(6))
    val closed = withAdSlots(records(6))
    assertThat(open.adCount()).isEqualTo(1)
    assertThat(closed.adCount()).isEqualTo(1)
    // The union would have produced a slot after record 10 instead of one trailing each.
    assertThat(withAdSlots(records(12)).adPositions()).isEqualTo(listOf(10))
  }
}
