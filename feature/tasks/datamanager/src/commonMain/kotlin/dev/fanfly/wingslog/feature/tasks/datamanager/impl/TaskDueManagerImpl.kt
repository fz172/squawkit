package dev.fanfly.wingslog.feature.tasks.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class TaskDueManagerImpl(
  private val clock: Clock = Clock.System,
  private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : TaskDueManager {

  override fun computeNextDue(
    card: MaintenanceTask,
    logs: List<MaintenanceLog>,
    allCards: List<MaintenanceTask>,
  ): DueMetadata = computeNextDueRecursive(card, logs, logs, allCards, mutableSetOf())

  private fun computeNextDueRecursive(
    card: MaintenanceTask,
    logs: List<MaintenanceLog>,
    allLogs: List<MaintenanceLog>,
    allCards: List<MaintenanceTask>,
    visited: MutableSet<String>,
  ): DueMetadata {
    if (card.id in visited) {
      // Cycle detected or already computed in this chain
      return DueMetadata(status = DueStatus.NORMAL)
    }
    visited.add(card.id)

    // 0. Check One-Time Completion
    val relevantLogs = logs.filter { card.id in it.inspection_ids }
      .sortedByDescending { it.timestamp?.getEpochSecond() ?: 0L }
    val latestLog = relevantLogs.firstOrNull()

    if (card.is_one_time && (latestLog != null || card.force_complied_status != null)) {
      return DueMetadata(status = DueStatus.COMPLIED)
    }

    // 1. Force overrides
    val forceDueDate = card.force_due_date
    val hasForcedDate = forceDueDate != null && (forceDueDate.getEpochSecond() > 0L)
    val hasForcedEngine = card.force_due_engine_hour > 0f

    // Determine which metric to track against based on component type
    // Airframe tracks airframe_time, others track engine_hour
    val currentMetricTime =
      if (card.component == ComponentType.COMPONENT_AIRFRAME) {
        allLogs.filter { it.airframe_time > 0.0 }.maxOfOrNull { it.airframe_time }?.toFloat() ?: 0f
      } else {
        allLogs.filter { it.engine_hour > 0.0 }.maxOfOrNull { it.engine_hour }?.toFloat() ?: 0f
      }

    val currentDate = clock.now().toLocalDateTime(timeZone).date

    if (hasForcedDate || hasForcedEngine) {
      val nextDueDate = if (hasForcedDate) {
        forceDueDate.toLocalDate(timeZone)
      } else null
      val nextDueEngine = if (hasForcedEngine) card.force_due_engine_hour else null

      val status = when {
        (nextDueDate != null && nextDueDate < currentDate) ||
          (nextDueEngine != null && nextDueEngine < currentMetricTime) -> DueStatus.OVERDUE

        (nextDueDate != null && nextDueDate <= currentDate.plus(1, DateTimeUnit.MONTH)) ||
          (nextDueEngine != null && nextDueEngine <= currentMetricTime + 10f) -> DueStatus.DUE_SOON

        else -> DueStatus.NORMAL
      }

      return DueMetadata(
        nextDueDate = nextDueDate,
        nextDueEngine = nextDueEngine,
        status = status
      )
    }

    // 2. Compute based on rules
    var nextDueDate: LocalDate? = null
    var nextDueEngine: Float? = null
    var isOnCondition = false
    var isImmediate = false

    for (rule in card.rules) {
      val timeRule = rule.time_rule
      val engineRule = rule.engine_hour_rule
      val onConditionRule = rule.on_condition_rule
      val linkedRule = rule.linked_rule
      val immediateRule = rule.immediate_rule

      when {
        timeRule != null -> {
          val baseDate = if (latestLog?.timestamp != null) {
            latestLog.timestamp!!.toLocalDate(timeZone)
              .also { logger.d { "TimeRule base date:  Latest log date: $it" } }
          } else {
            val creationDate = timeRule.creation_date
            if (creationDate != null && creationDate.getEpochSecond() > 0L) {
              creationDate.toLocalDate(timeZone)
                .also { logger.d { "TimeRule base date: Using rule creation date: $it" } }
            } else {
              logger.w { "TimeRule has no creation_date set; falling back to current date $currentDate" }
              currentDate
            }
          }
          val calculated = when {
            timeRule.interval_days > 0 -> baseDate.plus(timeRule.interval_days, DateTimeUnit.DAY)
            timeRule.interval_years > 0 -> baseDate.plus(timeRule.interval_years, DateTimeUnit.YEAR)
            // Month-based intervals snap to end-of-month so a task done mid-month
            // is due at the close of the calendar month it lands in
            // (e.g. logged 12/14/2025 + 12mo → due 12/31/2026).
            else -> baseDate.plus(timeRule.interval_months, DateTimeUnit.MONTH).endOfMonth()
          }
          if (nextDueDate == null || calculated < nextDueDate) {
            nextDueDate = calculated
          }
        }

        engineRule != null -> {
          val baseEngine =
            if (card.component == ComponentType.COMPONENT_AIRFRAME) {
              latestLog?.airframe_time?.toFloat() ?: 0f
            } else {
              latestLog?.engine_hour?.toFloat() ?: 0f
            }
          val calculated = baseEngine + engineRule.interval_hours
          if (nextDueEngine == null || calculated < nextDueEngine) {
            nextDueEngine = calculated
          }
        }

        onConditionRule != null -> {
          isOnCondition = true
        }

        immediateRule != null -> {
          isImmediate = true
        }

        linkedRule != null -> {
          val parentCard = allCards.find { it.id == linkedRule.parent_inspection_id }
          if (parentCard != null) {
            // Find when THIS card was last completed
            val latestLogEpoch = latestLog?.timestamp?.getEpochSecond() ?: 0L

            // Compute parent's due status as of the last time THIS card was completed.
            // This ensures that if the parent is done but THIS card is skipped,
            // THIS card remains due/overdue based on the OLD parent cycle.
            val parentLogs = if (latestLog == null) {
              emptyList()
            } else {
              allLogs.filter { (it.timestamp?.getEpochSecond() ?: 0L) <= latestLogEpoch }
            }

            val parentMetadata =
              computeNextDueRecursive(parentCard, parentLogs, allLogs, allCards, visited)

            // Inherit due properties from parent
            val pNextDate = parentMetadata.nextDueDate
            if (pNextDate != null && (nextDueDate == null || pNextDate < nextDueDate)) {
              nextDueDate = pNextDate
            }
            val pNextEngine = parentMetadata.nextDueEngine
            if (pNextEngine != null && (nextDueEngine == null || pNextEngine < nextDueEngine)) {
              nextDueEngine = pNextEngine
            }
            if (parentMetadata.isOnCondition) isOnCondition = true
            if (parentMetadata.isImmediate) isImmediate = true
          }
        }
      }
    }

    // 3. Force complied — skip to next cycle
    val forceComplied = card.force_complied_status
    if (forceComplied != null) {
      val compliedEpoch = forceComplied.complied_date?.getEpochSecond() ?: 0L
      val latestLogEpoch = latestLog?.timestamp?.getEpochSecond() ?: 0L

      // Apply only when no real log has superseded the force-comply action
      if (compliedEpoch > latestLogEpoch) {
        for (rule in card.rules) {
          rule.time_rule?.let { timeRule ->
            nextDueDate = nextDueDate?.let { d ->
              when {
                timeRule.interval_days > 0 -> d.plus(timeRule.interval_days, DateTimeUnit.DAY)
                timeRule.interval_years > 0 -> d.plus(timeRule.interval_years, DateTimeUnit.YEAR)
                else -> d.plus(timeRule.interval_months, DateTimeUnit.MONTH).endOfMonth()
              }
            }
          }
          rule.engine_hour_rule?.let { engineRule ->
            nextDueEngine = nextDueEngine?.let { it + engineRule.interval_hours }
          }
        }
      }
    }

    val status = when {
      isImmediate -> DueStatus.OVERDUE
      (nextDueDate != null && nextDueDate < currentDate) ||
        (nextDueEngine != null && nextDueEngine < currentMetricTime) -> DueStatus.OVERDUE

      (nextDueDate != null && nextDueDate <= currentDate.plus(1, DateTimeUnit.MONTH)) ||
        (nextDueEngine != null && nextDueEngine <= currentMetricTime + 10f) -> DueStatus.DUE_SOON

      else -> DueStatus.NORMAL
    }

    return DueMetadata(
      nextDueDate = nextDueDate,
      nextDueEngine = nextDueEngine,
      isOnCondition = isOnCondition,
      isImmediate = isImmediate,
      status = status
    )
  }

  companion object {
    private val logger = Logger.withTag("TaskDueManager")
  }
}

private fun LocalDate.endOfMonth(): LocalDate {
  val firstOfNextMonth = LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH)
  return firstOfNextMonth.minus(1, DateTimeUnit.DAY)
}

