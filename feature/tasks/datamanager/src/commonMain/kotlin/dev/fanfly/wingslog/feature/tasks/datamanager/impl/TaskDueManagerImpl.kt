package dev.fanfly.wingslog.feature.tasks.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.datetime.toLocalDate
import dev.fanfly.wingslog.core.template.readingFor
import dev.fanfly.wingslog.feature.tasks.datamanager.TaskDueManager
import dev.fanfly.wingslog.feature.tasks.datamanager.defaultMeterKey
import dev.fanfly.wingslog.feature.tasks.datamanager.forcedDueMeter
import dev.fanfly.wingslog.feature.tasks.datamanager.meterIntervalFor
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceTask
import dev.fanfly.wingslog.thing.TimeRule
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
    // The forced override and the meter it is measured in — keyed, falling back to the legacy
    // float for an override set before `force_due_meter` existed (#759).
    val forcedDue = card.forcedDueMeter()
    val hasForcedEngine = forcedDue != null
    val forcedMeterKey = forcedDue?.first ?: card.defaultMeterKey()

    /** The highest reading any log carries for [meterKey]. */
    fun currentReading(meterKey: String): Float =
      allLogs.mapNotNull { it.readingFor(meterKey) }
        .maxOrNull()
        ?.toFloat() ?: 0f

    // The meter the *forced* value is measured against. A rule names its own below — this is only
    // for comparing the override, which carries its key with it.
    val currentMetricTime = currentReading(forcedMeterKey)

    /**
     * How close to [interval] still counts as due-soon. Proportional because the window has to
     * mean the same thing in every unit: 10 hours out of a 100-hour inspection is a warning,
     * 10 miles out of a 5,000-mile oil change is not (#759).
     */
    fun dueSoonWindowFor(interval: Float): Float = interval * DUE_SOON_FRACTION

    val currentDate = clock.now()
      .toLocalDateTime(timeZone).date

    if (hasForcedDate || hasForcedEngine) {
      val nextDueDate = if (hasForcedDate) {
        forceDueDate.toLocalDate(timeZone)
      } else null
      val nextDueEngine = forcedDue?.second
      // An override carries no interval, so borrow one from a rule measured in the same meter.
      val window = card.rules.firstNotNullOfOrNull { card.meterIntervalFor(it) }
        ?.takeIf { (key, _) -> key == forcedMeterKey }
        ?.let { (_, interval) -> dueSoonWindowFor(interval) }
        ?: DEFAULT_DUE_SOON_WINDOW

      val status = when {
        (nextDueDate != null && nextDueDate < currentDate) ||
          (nextDueEngine != null && nextDueEngine < currentMetricTime) -> DueStatus.OVERDUE

        (nextDueDate != null && nextDueDate <= currentDate.plus(
          1,
          DateTimeUnit.MONTH
        )) ||
          (nextDueEngine != null && nextDueEngine <= currentMetricTime + window) -> DueStatus.DUE_SOON

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
    // Which rule is *closest to due*, measured in its own meter. Picking the smallest raw
    // `nextDueEngine` compared a 5,000-mile oil change against a 100-hour inspection and let the
    // arithmetic decide; remaining-until-due is the only comparison meaningful across units.
    var nextDueRemaining: Float? = null
    // Sized by the winning rule's interval; null when a linked rule supplied the due, which
    // carries no interval of its own.
    var dueSoonWindow: Float? = null

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
          val calculated = timeRule.advance(baseDate)
          if (nextDueDate == null || calculated < nextDueDate) {
            nextDueDate = calculated
          }
        }

        meterRule != null -> {
          val (meterKey, interval) = meterRule
          val base = latestLog?.readingFor(meterKey)
            ?.toFloat() ?: 0f
          val calculated = base + interval
          val remaining = calculated - currentReading(meterKey)
          if (nextDueRemaining == null || remaining < nextDueRemaining) {
            nextDueEngine = calculated
            nextDueMeterKey = meterKey
            nextDueRemaining = remaining
            dueSoonWindow = dueSoonWindowFor(interval)
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
            if (pNextEngine != null) {
              // The parent's due is measured in the parent's meter, so inherit the key with it —
              // otherwise a mileage parent hands down a number read as hours.
              val pMeterKey =
                parentMetadata.nextDueMeterKey ?: card.defaultMeterKey()
              val pRemaining = pNextEngine - currentReading(pMeterKey)
              if (nextDueRemaining == null || pRemaining < nextDueRemaining) {
                nextDueEngine = pNextEngine
                nextDueMeterKey = pMeterKey
                nextDueRemaining = pRemaining
                dueSoonWindow = null
              }
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
            nextDueDate = nextDueDate?.let { d ->
              var advanced = timeRule.advance(d)
              while (advanced <= currentDate) advanced = timeRule.advance(advanced)
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

    // Read fresh rather than reusing nextDueRemaining: the force-complied pass above advances
    // nextDueEngine past whatever the loop computed.
    val currentForNextDue =
      nextDueMeterKey?.let { currentReading(it) } ?: currentMetricTime
    val window = dueSoonWindow ?: DEFAULT_DUE_SOON_WINDOW

    val status = when {
      isImmediate -> DueStatus.OVERDUE
      (nextDueDate != null && nextDueDate < currentDate) ||
        (nextDueEngine != null && nextDueEngine < currentForNextDue) -> DueStatus.OVERDUE

      (nextDueDate != null && nextDueDate <= currentDate.plus(
        1,
        DateTimeUnit.MONTH
      )) ||
        (nextDueEngine != null && nextDueEngine <= currentForNextDue + window) -> DueStatus.DUE_SOON

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

    /**
     * Fraction of a rule's interval that still counts as due-soon. A tenth reproduces the fixed
     * 10-hour window this used to hard-code, which only ever fitted the 100-hour inspection it was
     * written for.
     */
    private const val DUE_SOON_FRACTION = 0.1f

    /** Used when the due came from somewhere with no interval to scale — a linked or forced due. */
    private const val DEFAULT_DUE_SOON_WINDOW = 10f
  }
}

/**
 * One interval on from [from].
 *
 * Month- and year-based intervals snap to the end of the month they land in — logged 14 Dec 2025
 * + 12 months is due 31 Dec 2026, which is what an annual inspection legally means — unless the
 * rule was written for a template that asked for the anniversary instead (PRD §4.6): a water
 * heater flushed on the 14th is next due on the 14th. Day intervals never snap.
 */
private fun TimeRule.advance(from: LocalDate): LocalDate {
  val landed = when {
    interval_days > 0 -> return from.plus(interval_days, DateTimeUnit.DAY)
    interval_years > 0 -> from.plus(interval_years, DateTimeUnit.YEAR)
    else -> from.plus(interval_months, DateTimeUnit.MONTH)
  }
  return if (due_on_anniversary) landed else landed.endOfMonth()
}

private fun LocalDate.endOfMonth(): LocalDate {
  val firstOfNextMonth = LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH)
  return firstOfNextMonth.minus(1, DateTimeUnit.DAY)
}
