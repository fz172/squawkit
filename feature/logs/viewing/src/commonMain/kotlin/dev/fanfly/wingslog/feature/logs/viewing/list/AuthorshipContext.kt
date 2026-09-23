package dev.fanfly.wingslog.feature.logs.viewing.list

import dev.fanfly.wingslog.core.analytics.log
import kotlinx.coroutines.flow.combine

/** The share-derived facts the log list needs, combined so they fit one slot of the outer combine. */
internal data class AuthorshipContext(
  val authors: Map<String, String?>,
  val names: Map<String, String>,
  val isShared: Boolean,
)
