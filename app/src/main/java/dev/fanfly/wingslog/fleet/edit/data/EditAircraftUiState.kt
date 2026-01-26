package dev.fanfly.wingslog.fleet.edit.data

import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.aircraft

data class EditAircraftUiState(
  val aircraft: Aircraft = aircraft {},
  val isLoading: Boolean = true,
  val isSaved: Boolean = false
)