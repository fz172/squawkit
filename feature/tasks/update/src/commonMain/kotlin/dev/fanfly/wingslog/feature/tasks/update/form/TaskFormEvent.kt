package dev.fanfly.wingslog.feature.tasks.update.form

sealed interface TaskFormEvent {
  data object PickError : TaskFormEvent
}
