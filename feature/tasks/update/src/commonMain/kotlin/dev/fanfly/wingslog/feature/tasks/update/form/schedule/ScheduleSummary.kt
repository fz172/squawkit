package dev.fanfly.wingslog.feature.tasks.update.form.schedule

/**
 * The schedule as the preview banner says it: one [line], a [hint] for the second line, and
 * whether there is a schedule at all yet.
 */
internal data class ScheduleSummary(val line: String, val hint: String, val isEmpty: Boolean)
