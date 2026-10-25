package dev.fanfly.wingslog.feature.dashboard.host

import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import dev.fanfly.wingslog.thing.Squawk
import kotlinx.coroutines.flow.combine

/** The share-related flows, combined so they fit in one slot of the outer [combine]. */
internal data class ShareContext(
  val squawks: List<Squawk>,
  val myRole: ShareRole?,
  val shared: Boolean,
)
