package dev.fanfly.wingslog.feature.thing.dashboard

import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
import dev.fanfly.wingslog.feature.attachment.model.DataLogRowInfo
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.thing.Squawk
import kotlinx.coroutines.flow.combine

/** The share-related flows, combined so they fit in one slot of the outer [combine]. */
internal data class ShareContext(
  val squawks: List<Squawk>,
  val syncStates: Map<String, BlobSyncState>,
  val myRole: ShareRole?,
  val shared: Boolean,
  val dataLogs: Map<DataLogId, DataLogRowInfo>,
)
