package dev.fanfly.wingslog.feature.tasks.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.template.readingFor
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager
import dev.fanfly.wingslog.feature.tasks.datamanager.defaultMeterKey
import dev.fanfly.wingslog.feature.tasks.datamanager.meterIntervalFor
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceTask
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class TaskDueManagerImpl(
  private val clock: Clock = Clock.System,
  private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : TaskDueManager {

  override fun computeNextDue(
    card: MaintenanceTask,
    logs: List<MaintenanceLog>,
    allCards: List<MaintenanceTask>,
  ): DueMetadata =
    computeNextDueRecursive(card, logs, logs, allCards, mutableSetOf())

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
    val hasForcedDate =
      forceDueDate != null && (forceDueDate.getEpochSecond() > 0L)
    // The forced override, keyed. `force_due_meter` supersedes `force_due_engine_hour`, which
    // could only ever mean engine hours (#759).
    val forcedMeter = card.force_due_meter?.takeIf { it.value_ > 0.0 }
    val hasForcedEngine = forcedMeter != null || card.force_due_engine_hour > 0f
    val forcedMeterKey = forcedMeter?.meter_key ?: card.defaultMeterKey()

    /** The highest reading any log carries for [meterKey]. */
    fun currentReading(meterKey: String): Float =
      allLogs.mapNotNull { it.readingFor(meterKey) }
        .maxOrNull()
        ?.toFloat() ?: 0f

    // The meter the *forced* value is measured against. A rule names its own below — this is only
    // for comparing the override, which carries its key with it.
    val currentMetricTime = currentReading(forcedMeterKey)

    val currentDate = clock.now()
      .toLocalDateTime(timeZone).date

    if (hasForcedDate || hasForcedEngine) {
      val nextDueDate = if (hasForcedDate) {
        forceDueDate.toLocalDate(timeZone)
      } else null
      val nextDueEngine = when {
        forcedMeter != null -> forcedMeter.value_.toFloat()
        card.force_due_engine_hour > 0f -> card.force_due_engine_hour
        else -> null
      }

      val status = when {
        (nextDueDate != null && nextDueDate < currentDate) ||
          (nextDueEngine != null && nextDueEngine < currentMetricTime) -> DueStatus.OVERDUE

        (nextDueDate != null && nextDueDate <= currentDate.plus(
          1,
          DateTimeUnit.MONTH
        )) ||
          (nextDueEngine != null && nextDueEngine <= currentMetricTime + 10f) -> DueStatus.DUE_SOON

        else -> DueStatus.NORMAL
      }

      return DueMetadata(
        nextDueDate = nextDueDate,
        nextDueEngine = nextDueEngine,
        nextDueMeterKey = forcedMeterKey,
        status = status
      )
    }

    // 2. Compute based on rules
    var nextDueDate: LocalDate? = null
    var nextDueEngine: Float? = null
    var isOnCondition = false
    var isImmediate = false
    var nextDueMeterKey: String? = null

    for (rule in card.rules) {
      val timeRule = rule.time_rule
      // A MeterRule carries its key; an EngineHourRule means whichever meter this card's component
      // has always implied. Both land here so an existing aviation task computes exactly as before.
      val meterRule = card.meterIntervalFor(rule)
      val onConditionRule = rule.on_condition_rule
      val linkedRule = rule.linked_rule
      val immediateRule = rule.immediate_rule

      when {
        timeRule != null -> {
          val baseDate = if (latestLog?.timestamp != null) {
            latestLog.timestamp!!.toLocalDate(timeZone)
              .also { logger.d { "TimeRule base date: using latest log date: $it" } }
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
            timeRule.interval_days > 0 -> baseDate.plus(
              timeRule.interval_days,
              DateTimeUnit.DAY
            )
            // Month- and year-based intervals snap to end-of-month so a task done
            // mid-month is due at the close of the calendar month it lands in
            // (e.g. logged 12/14/2025 + 12mo → due 12/31/2026).
            timeRule.interval_years > 0 -> baseDate.plus(
              timeRule.interval_years,
              DateTimeUnit.YEAR
            )
              .endOfMonth()

            else -> baseDate.plus(timeRule.interval_months, DateTimeUnit.MONTH)
              .endOfMonth()
          }
          if (nextDueDate == null || calculated < nextDueDate) {
            nextDueDate = calculated
          }
        }

        meterRule != null -> {
          val (meterKey, interval) = meterRule
          val base = latestLog?.readingFor(meterKey)
            ?.toFloat() ?: 0f
          val calculated = base + interval
          if (nextDueEngine == null || calculated < nextDueEngine) {
            nextDueEngine = calculated
            nextDueMeterKey = meterKey
          }
        }

        onConditionRule != null -> {
          isOnCondition = true
        }

        immediateRule != null -> {
          isImmediate = true
        }

        linkedRule != null -> {
          val parentCard =
            allCards.find { it.id == linkedRule.parent_inspection_id }
          if (parentCard != null) {
            // Find when THIS card was last completed
            val latestLogEpoch = latestLog?.timestamp?.getEpochSecond() ?: 0L

            // Compute parent's due status as of the last time THIS card was completed.
            // This ensures that if the parent is done but THIS card is skipped,
            // THIS card remains due/overdue based on the OLD parent cycle.
            val parentLogs = if (latestLog == null) {
              emptyList()
            } else {
              allLogs.filter {
                (it.timestamp?.getEpochSecond() ?: 0L) <= latestLogEpoch
              }
            }

            val parentMetadata =
              computeNextDueRecursive(
                parentCard,
                parentLogs,
                allLogs,
                allCards,
                visited
              )

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

    // 3. Force complied — advance past ALL overdue cycles, not just one
    val forceComplied = card.force_complied_status
    if (forceComplied != null) {
      val compliedEpoch = forceComplied.complied_date?.getEpochSecond() ?: 0L
      val latestLogEpoch = latestLog?.timestamp?.getEpochSecond() ?: 0L

      // Apply only when no real log has superseded the force-comply action
      if (compliedEpoch > latestLogEpoch) {
        for (rule in card.rules) {
          rule.time_rule?.let { timeRule ->
            fun LocalDate.advance(): LocalDate = when {
              timeRule.interval_days > 0 -> plus(
                timeRule.interval_days,
                DateTimeUnit.DAY
              )

              timeRule.interval_years > 0 -> plus(
                timeRule.interval_years,
                DateTimeUnit.YEAR
              ).endOfMonth()

              else -> plus(
                timeRule.interval_months,
                DateTimeUnit.MONTH
              ).endOfMonth()
            }
            nextDueDate = nextDueDate?.let { d ->
              var advanced = d.advance()
              while (advanced <= currentDate) advanced = advanced.advance()
              advanced
            }
          }
          card.meterIntervalFor(rule)
            ?.let { (meterKey, interval) ->
              if (interval > 0f) {
                val current = currentReading(meterKey)
                nextDueEngine = nextDueEngine?.let { e ->
                  var advanced = e + interval
                  while (advanced <= current) advanced += interval
                  advanced
                }
              }
            }
        }
      }
    }

    val status = when {
      isImmediate -> DueStatus.OVERDUE
      (nextDueDate != null && nextDueDate < currentDate) ||
        (nextDueEngine != null && nextDueEngine < currentMetricTime) -> DueStatus.OVERDUE

      (nextDueDate != null && nextDueDate <= currentDate.plus(
        1,
        DateTimeUnit.MONTH
      )) ||
        (nextDueEngine != null && nextDueEngine <= currentMetricTime + 10f) -> DueStatus.DUE_SOON

      else -> DueStatus.NORMAL
    }

    return DueMetadata(
      nextDueDate = nextDueDate,
      nextDueEngine = nextDueEngine,
      // Set by whichever rule produced nextDueEngine above. Without it every card fell back to
      // hours, so a car scheduled in miles still read "5000.0 HRS" (#759).
      nextDueMeterKey = nextDueMeterKey,
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
