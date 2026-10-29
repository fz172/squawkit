package dev.fanfly.wingslog.feature.tasks.dashboard

import dev.fanfly.wingslog.feature.ads.model.ListRow
import dev.fanfly.wingslog.feature.ads.model.withAdSlotsGrouped
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.feature.tasks.model.MaintenanceTaskWithStatus

/** Most urgent first — the order the status groups are listed in. */
val TASK_DUE_TIERS = listOf(
  DueStatus.OVERDUE,
  DueStatus.DUE_SOON,
  DueStatus.NORMAL,
)

/** An unset or foreign status falls back to Normal. */
val MaintenanceTaskWithStatus.dueTier: DueStatus
  get() = dueStatus.status.takeIf { it in TASK_DUE_TIERS } ?: DueStatus.NORMAL

/** One item of the task list for [ComplianceSection]. [key] is unique in the list and stable. */
sealed interface TaskListLine {
  val key: String

  /** Names a due status tier and says how many tasks sit under it. */
  data class StatusHeader(val status: DueStatus, val count: Int) : TaskListLine {
    override val key: String get() = "status-${status.name}"
  }

  /** One line of the grid: up to `columns` tasks, a single one on a phone. */
  data class Records(val items: List<MaintenanceTaskWithStatus>) : TaskListLine {
    override val key: String get() = items.first().card.id
  }

  data class Ad(val slotIndex: Int) : TaskListLine {
    override val key: String get() = "ad-$slotIndex"
  }
}

/**
 * Flattens [tasks] into the lines [ComplianceSection] renders.
 * [grouped] groups tasks under [TaskListLine.StatusHeader] for each tier; empty tiers get no header.
 * A header or an ad always ends the line of the grid before it.
 */
fun taskListLines(
  tasks: List<MaintenanceTaskWithStatus>,
  grouped: Boolean,
  columns: Int,
  showAds: Boolean,
): List<TaskListLine> {
  val entries: List<TaskListLine> = if (grouped) {
    TASK_DUE_TIERS.flatMap { tier ->
      val inTier = tasks.filter { it.dueTier == tier }
      if (inTier.isEmpty()) {
        emptyList()
      } else {
        listOf(TaskListLine.StatusHeader(tier, inTier.size)) +
          inTier.map { TaskListLine.Records(listOf(it)) }
      }
    }
  } else {
    tasks.map { TaskListLine.Records(listOf(it)) }
  }
  val rows = if (showAds) {
    withAdSlotsGrouped(entries) { it is TaskListLine.StatusHeader }
  } else {
    entries.map { ListRow.Item(it) }
  }

  val perLine = columns.coerceAtLeast(1)
  return buildList {
    val run = ArrayList<MaintenanceTaskWithStatus>(perLine)
    fun flush() {
      if (run.isNotEmpty()) add(TaskListLine.Records(run.toList()))
      run.clear()
    }
    rows.forEach { row ->
      val entry = when (row) {
        is ListRow.Ad -> TaskListLine.Ad(row.slotIndex)
        is ListRow.Item -> row.value
      }
      if (entry is TaskListLine.Records) {
        run += entry.items
        if (run.size == perLine) flush()
      } else {
        flush()
        add(entry)
      }
    }
    flush()
  }
}
