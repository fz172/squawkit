package dev.fanfly.wingslog.feature.datalog.datamanager

import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.datalog.model.ImportProgress
import dev.fanfly.wingslog.id.ThingId
import kotlinx.coroutines.flow.Flow

/** Turns a picked file into a synced record plus a blob (design §6.1). */
interface DataLogImporter {
  /**
   * Emits [ImportProgress] until a terminal state. A probable duplicate (same recorder, same start)
   * ends in `NeedsConfirmation`; re-running with [confirmDuplicate] keeps both.
   */
  fun import(thingId: ThingId, picked: PickedFile, confirmDuplicate: Boolean = false): Flow<ImportProgress>
}

/** The Thing's own identifier (its tail number on the airplane preset), for PRD R11. */
fun interface ThingIdentifierLookup {
  suspend fun identifierOf(thingId: ThingId): String?
}
