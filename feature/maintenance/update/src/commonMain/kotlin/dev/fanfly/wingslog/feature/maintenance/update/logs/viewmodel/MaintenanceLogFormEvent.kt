package dev.fanfly.wingslog.feature.maintenance.update.logs.viewmodel

sealed interface MaintenanceLogFormEvent {
  data object SaveSuccess : MaintenanceLogFormEvent
  data object DeleteSuccess : MaintenanceLogFormEvent
  data object FileAdded : MaintenanceLogFormEvent
  data object LinkAdded : MaintenanceLogFormEvent
  data object PickError : MaintenanceLogFormEvent
}