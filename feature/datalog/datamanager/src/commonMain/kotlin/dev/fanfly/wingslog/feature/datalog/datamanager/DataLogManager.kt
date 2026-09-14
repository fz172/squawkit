package dev.fanfly.wingslog.feature.datalog.datamanager

import dev.fanfly.wingslog.datalog.DataLog
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
import dev.fanfly.wingslog.feature.attachment.model.DownloadState
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.datalog.model.DataLogSeriesData
import dev.fanfly.wingslog.feature.datalog.model.ImportProgress
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId
import kotlinx.coroutines.flow.Flow

/** The data logs of a Thing (design §7). Every id is typed; scope always comes from the resolver. */
interface DataLogManager {
  /** Newest first. Rows without an id are corrupt and dropped. */
  fun observe(thingId: ThingId): Flow<List<DataLog>>
  fun observeOne(thingId: ThingId, id: DataLogId): Flow<DataLog?>
  fun import(thingId: ThingId, file: PickedFile, confirmDuplicate: Boolean = false): Flow<ImportProgress>

  /** Schedules the download of a remote-only raw file and reports until the bytes are local. */
  fun ensureLocal(thingId: ThingId, id: DataLogId): Flow<DownloadState>

  /** Cache → local blob → decode → parse. Fails when the bytes are not local yet. */
  suspend fun load(thingId: ThingId, id: DataLogId): Result<DataLogSeriesData>

  /** Tombstones the record; the blob follows through the tombstone GC and the server. */
  suspend fun delete(thingId: ThingId, id: DataLogId): Result<Unit>
  fun observeBlobState(thingId: ThingId, id: DataLogId): Flow<BlobSyncState?>
}
