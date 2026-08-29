package dev.fanfly.wingslog.feature.squawk.datamanager

import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.aircraft.SquawkDismissReason
import kotlinx.coroutines.flow.Flow

interface SquawkManager {
  fun observeSquawks(thingId: String): Flow<List<Squawk>>
  suspend fun addSquawk(thingId: String, squawk: Squawk): Result<Boolean>
  suspend fun updateSquawk(thingId: String, squawk: Squawk): Result<Boolean>
  suspend fun deleteSquawk(
    thingId: String,
    squawkId: String
  ): Result<Boolean>

  suspend fun markAddressed(
    thingId: String,
    squawkIds: List<String>,
    logId: String,
  ): Result<Unit>

  suspend fun dismissSquawk(
    thingId: String,
    squawkId: String,
    reason: SquawkDismissReason,
  ): Result<Unit>

  suspend fun reopenSquawk(thingId: String, squawkId: String): Result<Unit>
}
