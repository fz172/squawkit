package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.thing.ScheduleType

/**
 * The schedule types a template's task form offers, from what it [declared].
 *
 * An empty list is CALENDAR + METER — not because that is a sensible default, but because it is
 * what the field's absence meant on every aeroplane whose DNA predates it, and on the Things with
 * no DNA at all. Every canonical preset declares the list explicitly; the validator insists.
 */
fun scheduleTypesOffered(declared: List<ScheduleType>): Set<ScheduleType> =
  declared.filter { it != ScheduleType.SCHEDULE_TYPE_UNKNOWN }
    .toSet()
    .ifEmpty { LEGACY_SCHEDULE_TYPES }

val LEGACY_SCHEDULE_TYPES: Set<ScheduleType> =
  setOf(ScheduleType.SCHEDULE_TYPE_CALENDAR, ScheduleType.SCHEDULE_TYPE_METER)
