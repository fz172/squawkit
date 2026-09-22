package dev.fanfly.wingslog.core.template

import dev.fanfly.wingslog.thing.MeterDef

/** A value on a meter the template declares — a log's reading, resolved to the meter itself. */
data class MeterValue(val meter: MeterDef, val value: Double)
