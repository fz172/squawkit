package dev.fanfly.wingslog.feature.technician.datamanager

import dev.fanfly.wingslog.aircraft.Technician
import kotlinx.coroutines.flow.Flow

interface TechnicianManager {
  fun observeTechnicians(): Flow<List<Technician>>

  fun loadTechnician(id: String): Flow<Technician?>

  /** Resolves the current user's technician record via UserInfo.self_technician_id. */
  fun observeSelf(): Flow<Technician?>

  /** The raw self-technician ID — exposed separately for list-row badging without double-loading. */
  fun observeSelfId(): Flow<String?>

  suspend fun updateTechnician(technician: Technician): Result<Boolean>

  suspend fun deleteTechnician(id: String): Result<Boolean>

  /**
   * Creates or updates the current user's self-technician record with [name].
   * Used during onboarding when the user enters their display name.
   * Handles anonymous users who have no bootstrapped technician record yet.
   */
  suspend fun saveSelfName(name: String): Result<Unit>
}
