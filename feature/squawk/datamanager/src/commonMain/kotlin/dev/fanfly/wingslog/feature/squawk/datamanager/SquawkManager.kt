package dev.fanfly.wingslog.feature.squawk.datamanager

import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.aircraft.SquawkDismissReason
import kotlinx.coroutines.flow.Flow

interface SquawkManager {
  fun observeSquawks(aircraftId: String): Flow<List<Squawk>>
  suspend fun addSquawk(aircraftId: String, squawk: Squawk): Result<Boolean>
  suspend fun updateSquawk(aircraftId: String, squawk: Squawk): Result<Boolean>
  suspend fun deleteSquawk(
    aircraftId: String,
    squawkId: String
  ): Result<Boolean>

  suspend fun markAddressed(
    aircraftId: String,
    squawkIds: List<String>,
    logId: String,
  ): Result<Unit>

  suspend fun dismissSquawk(
    aircraftId: String,
    squawkId: String,
    reason: SquawkDismissReason,
  ): Result<Unit>

  suspend fun reopenSquawk(aircraftId: String, squawkId: String): Result<Unit>
}
