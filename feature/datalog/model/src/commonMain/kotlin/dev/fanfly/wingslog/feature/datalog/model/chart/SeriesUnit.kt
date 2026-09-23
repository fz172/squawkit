package dev.fanfly.wingslog.feature.datalog.model.chart

import dev.fanfly.wingslog.feature.datalog.model.SeriesKey

/** A series in a pane and the unit it was recorded in, which decides the axis it reads on. */
data class SeriesUnit(val key: SeriesKey, val unit: String)
