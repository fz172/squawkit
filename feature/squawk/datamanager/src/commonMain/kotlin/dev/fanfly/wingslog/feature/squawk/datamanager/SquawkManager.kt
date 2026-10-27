package dev.fanfly.wingslog.feature.squawk.datamanager

import dev.fanfly.wingslog.id.MaintenanceLogId
import dev.fanfly.wingslog.id.SquawkId
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.thing.Squawk
import dev.fanfly.wingslog.thing.SquawkDismissReason
import kotlinx.coroutines.flow.Flow

interface SquawkManager {
  fun observeSquawks(thingId: ThingId): Flow<List<Squawk>>

  suspend fun addSquawk(thingId: ThingId, squawk: Squawk): Result<Boolean>

  suspend fun updateSquawk(thingId: ThingId, squawk: Squawk): Result<Boolean>

  suspend fun deleteSquawk(
    thingId: ThingId,
    squawkId: SquawkId,
  ): Result<Boolean>

  suspend fun markAddressed(
    thingId: ThingId,
    squawkIds: List<SquawkId>,
    logId: MaintenanceLogId,
  ): Result<Unit>

  suspend fun dismissSquawk(
    thingId: ThingId,
    squawkId: SquawkId,
    reason: SquawkDismissReason,
  ): Result<Unit>

  suspend fun reopenSquawk(thingId: ThingId, squawkId: SquawkId): Result<Unit>
}
