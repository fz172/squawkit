package dev.fanfly.wingslog.feature.thing.dashboard.data

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.squawk.model.SquawkStatus
import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.thing.Squawk
import dev.fanfly.wingslog.thing.SquawkPriority
import org.junit.Test

class SquawkListLinesTest {

  private fun squawk(id: String, priority: SquawkPriority) =
    SquawkWithStatus(Squawk(id = id, priority = priority), SquawkStatus.OPEN)

  private val aog = squawk("aog", SquawkPriority.SQUAWK_PRIORITY_AOG)
  private val medium1 = squawk("m1", SquawkPriority.SQUAWK_PRIORITY_MEDIUM)
  private val medium2 = squawk("m2", SquawkPriority.SQUAWK_PRIORITY_MEDIUM)
  private val medium3 = squawk("m3", SquawkPriority.SQUAWK_PRIORITY_MEDIUM)
  private val unset = squawk("u", SquawkPriority.SQUAWK_PRIORITY_UNKNOWN)

  private fun header(tier: SquawkPriority, count: Int) =
    SquawkListLine.TierHeader(tier, count)

  private fun records(vararg items: SquawkWithStatus) =
    SquawkListLine.Records(items.toList())

  @Test
  fun grouped_headsEachTierAndSkipsEmptyOnes() {
    val lines = squawkListLines(
      listOf(aog, medium1, medium2),
      grouped = true,
      columns = 1,
      showAds = false,
    )

    assertThat(lines).containsExactly(
      header(SquawkPriority.SQUAWK_PRIORITY_AOG, 1),
      records(aog),
      header(SquawkPriority.SQUAWK_PRIORITY_MEDIUM, 2),
      records(medium1),
      records(medium2),
    )
      .inOrder()
  }

  @Test
  fun grouped_unsetPriorityFallsUnderLow() {
    val lines = squawkListLines(
      listOf(unset),
      grouped = true,
      columns = 1,
      showAds = false
    )

    assertThat(lines.first()).isEqualTo(
      header(
        SquawkPriority.SQUAWK_PRIORITY_LOW,
        1
      )
    )
  }

  @Test
  fun grouped_aLineOfTheGridNeverCrossesAHeader() {
    val lines = squawkListLines(
      listOf(aog, medium1, medium2, medium3),
      grouped = true,
      columns = 2,
      showAds = false,
    )

    assertThat(lines).containsExactly(
      header(SquawkPriority.SQUAWK_PRIORITY_AOG, 1),
      records(aog),
      header(SquawkPriority.SQUAWK_PRIORITY_MEDIUM, 3),
      records(medium1, medium2),
      records(medium3),
    )
      .inOrder()
  }

  @Test
  fun ungrouped_keepsTheIncomingOrderWithoutHeaders() {
    val lines = squawkListLines(
      listOf(medium1, aog),
      grouped = false,
      columns = 1,
      showAds = false,
    )

    assertThat(lines).containsExactly(records(medium1), records(aog))
      .inOrder()
  }

  @Test
  fun ads_shortListGetsOneSlotAfterTheLastRecord() {
    val lines = squawkListLines(
      listOf(aog, medium1),
      grouped = true,
      columns = 1,
      showAds = true
    )

    assertThat(lines.last()).isEqualTo(SquawkListLine.Ad(slotIndex = 0))
    assertThat(lines.filterIsInstance<SquawkListLine.Ad>()).hasSize(1)
  }

  @Test
  fun keys_areUnique() {
    val lines = squawkListLines(
      listOf(aog, medium1, medium2, medium3),
      grouped = true,
      columns = 2,
      showAds = true,
    )

    assertThat(lines.map { it.key }).containsNoDuplicates()
  }
}
