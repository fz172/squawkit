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
  fun import(
    thingId: ThingId,
    picked: PickedFile,
    confirmDuplicate: Boolean = false,
    /** PRD R12: keep the log here although its identity names another Thing. */
    keepIdentity: Boolean = false,
  ): Flow<ImportProgress>
}

/** Another Thing the user can see whose identifier a log carries (PRD R12). */
data class OtherThing(val id: ThingId, val name: String)

fun interface OtherThingLookup {
  suspend fun thingWithIdentifier(identity: String, excluding: ThingId): OtherThing?
}

/** The Thing's own identifier (its tail number on the airplane preset), for PRD R11. */
fun interface ThingIdentifierLookup {
  suspend fun identifierOf(thingId: ThingId): String?
}
