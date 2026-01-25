package dev.fanfly.wingslog.fleet.manage.data

import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.aircraft

data class ManageAircraftUiState(
  val aircraft: Aircraft = aircraft {},
  val isLoading: Boolean = true,
  val isSaved: Boolean = false
)