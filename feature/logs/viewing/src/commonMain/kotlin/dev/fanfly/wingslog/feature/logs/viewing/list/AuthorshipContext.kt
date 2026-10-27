package dev.fanfly.wingslog.feature.logs.viewing.list

import dev.fanfly.wingslog.id.MaintenanceLogId

/** The share-derived facts the log list needs, combined so they fit one slot of the outer combine. */
internal data class AuthorshipContext(
  val authors: Map<MaintenanceLogId, String?>,
  val names: Map<String, String>,
  val isShared: Boolean,
)
