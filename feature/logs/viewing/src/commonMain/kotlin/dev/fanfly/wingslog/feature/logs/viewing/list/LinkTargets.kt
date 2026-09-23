package dev.fanfly.wingslog.feature.logs.viewing.list

import dev.fanfly.wingslog.core.analytics.log
import dev.fanfly.wingslog.thing.MaintenanceTask
import dev.fanfly.wingslog.thing.Squawk

/** Task cards and squawks a log can link to, resolved for the detail sheet's link rows. */
internal data class LinkTargets(
  val cards: List<MaintenanceTask>,
  val squawks: List<Squawk>,
)
