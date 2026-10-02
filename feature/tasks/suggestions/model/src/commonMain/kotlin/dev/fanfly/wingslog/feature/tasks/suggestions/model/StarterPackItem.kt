package dev.fanfly.wingslog.feature.tasks.suggestions.model

import dev.fanfly.wingslog.thing.StarterTask

data class StarterPackItem(
  val task: StarterTask,
  val selected: Boolean,
)
