package dev.fanfly.wingslog.feature.squawk.dashboard

import dev.fanfly.wingslog.feature.squawk.model.SquawkWithStatus
import dev.fanfly.wingslog.thing.Squawk

data class SquawkOverviewUiState(
  val loaded: Boolean = false,
  val aogSquawks: List<Squawk> = emptyList(),
  val openPreview: List<SquawkWithStatus> = emptyList(),
)
