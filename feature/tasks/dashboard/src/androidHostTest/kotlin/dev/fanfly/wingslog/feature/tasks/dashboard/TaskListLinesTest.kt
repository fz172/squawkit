package dev.fanfly.wingslog.feature.tasks.dashboard

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus
import dev.fanfly.wingslog.task.MaintenanceTask
import org.junit.Test

class TaskListLinesTest {

  private fun task(id: String, status: DueStatus) =
    MaintenanceTaskWithStatus(
      MaintenanceTask(id = id, title = id),
      DueMetadata(status = status)
    )

  private val overdue1 = task("o1", DueStatus.OVERDUE)
  private val overdue2 = task("o2", DueStatus.OVERDUE)
  private val dueSoon1 = task("d1", DueStatus.DUE_SOON)
  private val dueSoon2 = task("d2", DueStatus.DUE_SOON)
  private val dueSoon3 = task("d3", DueStatus.DUE_SOON)
  private val normal1 = task("n1", DueStatus.NORMAL)

  private fun header(status: DueStatus, count: Int) =
    TaskListLine.StatusHeader(status, count)

  private fun records(vararg items: MaintenanceTaskWithStatus) =
    TaskListLine.Records(items.toList())

  @Test
  fun grouped_headsEachTierAndSkipsEmptyOnes() {
    val lines = taskListLines(
      listOf(overdue1, dueSoon1, dueSoon2, normal1),
      grouped = true,
      columns = 1,
      showAds = false,
    )

    assertThat(lines).containsExactly(
      header(DueStatus.OVERDUE, 1),
      records(overdue1),
      header(DueStatus.DUE_SOON, 2),
      records(dueSoon1),
      records(dueSoon2),
      header(DueStatus.NORMAL, 1),
      records(normal1),
    ).inOrder()
  }

  @Test
  fun grouped_skipsEmptyTiers() {
    val lines = taskListLines(
      listOf(overdue1, normal1),
      grouped = true,
      columns = 1,
      showAds = false,
    )

    assertThat(lines).containsExactly(
      header(DueStatus.OVERDUE, 1),
      records(overdue1),
      header(DueStatus.NORMAL, 1),
      records(normal1),
    ).inOrder()
  }

  @Test
  fun grouped_aLineOfTheGridNeverCrossesAHeader() {
    val lines = taskListLines(
      listOf(overdue1, dueSoon1, dueSoon2, dueSoon3),
      grouped = true,
      columns = 2,
      showAds = false,
    )

    assertThat(lines).containsExactly(
      header(DueStatus.OVERDUE, 1),
      records(overdue1),
      header(DueStatus.DUE_SOON, 3),
      records(dueSoon1, dueSoon2),
      records(dueSoon3),
    ).inOrder()
  }

  @Test
  fun ungrouped_keepsIncomingOrderWithoutHeaders() {
    val lines = taskListLines(
      listOf(normal1, overdue1),
      grouped = false,
      columns = 1,
      showAds = false,
    )

    assertThat(lines).containsExactly(records(normal1), records(overdue1))
      .inOrder()
  }

  @Test
  fun ads_shortListGetsOneSlotAfterTheLastRecord() {
    val lines = taskListLines(
      listOf(overdue1, dueSoon1),
      grouped = true,
      columns = 1,
      showAds = true,
    )

    assertThat(lines.last()).isEqualTo(TaskListLine.Ad(slotIndex = 0))
    assertThat(lines.filterIsInstance<TaskListLine.Ad>()).hasSize(1)
  }

  @Test
  fun keys_areUnique() {
    val lines = taskListLines(
      listOf(overdue1, overdue2, dueSoon1, dueSoon2, normal1),
      grouped = true,
      columns = 2,
      showAds = true,
    )

    assertThat(lines.map { it.key }).containsNoDuplicates()
  }
}
