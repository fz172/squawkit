package dev.fanfly.wingslog.feature.stresstest.fixtures

import dev.fanfly.wingslog.thing.SquawkPriority.SQUAWK_PRIORITY_HIGH
import dev.fanfly.wingslog.thing.SquawkPriority.SQUAWK_PRIORITY_LOW
import dev.fanfly.wingslog.thing.SquawkPriority.SQUAWK_PRIORITY_MEDIUM

/**
 * Custom declares nothing, so its fixture is the one that has to work with no vocabulary: the
 * wording here fits anything and names no part, meter, or trade.
 */
internal object CustomFixtures {

  private val TASKS = listOf(
    routine("Routine Inspection", months(6)),
    routine("Annual Service", months(12)),
  )

  private val SQUAWKS = listOf(
    SquawkTemplate(
      "Unfamiliar noise while running",
      "A new rattle that was not there last month. Comes and goes; not yet traced to anything.",
      SQUAWK_PRIORITY_LOW,
    ),
    SquawkTemplate(
      "Intermittent fault",
      "Stops working for a few seconds at a time, then carries on as if nothing happened.",
      SQUAWK_PRIORITY_MEDIUM,
    ),
    SquawkTemplate(
      "Will not power on",
      "Completely dead this morning. Checked the obvious; nothing tripped or unplugged.",
      SQUAWK_PRIORITY_HIGH,
    ),
    SquawkTemplate(
      "Visible wear on a moving part",
      "Noticeably more worn than at the last inspection. Still working, but worth watching.",
      SQUAWK_PRIORITY_LOW,
    ),
    SquawkTemplate(
      "Small leak",
      "A few drops underneath after each use. Not enough to matter yet.",
      SQUAWK_PRIORITY_MEDIUM,
    ),
    SquawkTemplate(
      "Overdue for service",
      "Past the interval on the label. Nothing wrong with it, just late.",
      SQUAWK_PRIORITY_LOW,
    ),
  )

  private val LOGS = listOf(
    LogTemplate(
      "Routine inspection completed. Everything checked and working as expected; no faults found.",
      taskHints = listOf("Routine Inspection"),
    ),
    LogTemplate(
      "Annual service. Cleaned, lubricated, consumables replaced, and all fasteners checked.",
      taskHints = listOf("Annual Service", "Routine Inspection"),
    ),
    LogTemplate("Cleaned and lubricated. Ran for ten minutes afterwards with no noise."),
    LogTemplate("Replaced the worn part with a new one. Old part kept for comparison."),
    LogTemplate("Traced the intermittent fault to a loose connection. Re-seated and secured."),
    LogTemplate("Tightened the fitting that was leaking. Dry after a full day of use."),
    LogTemplate("Found the dead unit had lost power at the supply. Restored and tested."),
  )

  val POOL = FakeDataPool(tasks = TASKS, squawks = SQUAWKS, logs = LOGS)
}
