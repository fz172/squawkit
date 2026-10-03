package dev.fanfly.wingslog.feature.tasks.suggestions.model

import dev.fanfly.wingslog.rpc.suggesttasks.TaskSuggestion
import dev.fanfly.wingslog.task.StarterTask

/** One card on the suggestions screen. */
data class StarterPackItem(
  /** What the card shows. From the server, it is also what accepting writes (design §6.8). */
  val suggestion: TaskSuggestion,
  val selected: Boolean,
  /**
   * The template's own starter task the card was made from, while the app's pack is still what
   * production offers (until the flag removal, T25). Accepting writes it as before. Null for a
   * suggestion from the server.
   */
  val starterTask: StarterTask? = null,
)
