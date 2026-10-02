package dev.fanfly.wingslog.feature.tasks.suggestions.model

import dev.fanfly.wingslog.task.StarterTask

data class StarterPackItem(
  val task: StarterTask,
  val selected: Boolean,
)
