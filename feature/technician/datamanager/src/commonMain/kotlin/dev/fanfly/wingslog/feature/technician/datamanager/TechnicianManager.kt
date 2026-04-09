package dev.fanfly.wingslog.feature.technician.datamanager

import dev.fanfly.wingslog.aircraft.Technician
import kotlinx.coroutines.flow.Flow

interface TechnicianManager {
  /**
   * Observes the list of all technicians for the current user.
   */
  fun observeTechnicians(): Flow<List<Technician>>

  /**
   * Loads a specific technician by ID.
   */
  fun loadTechnician(id: String): Flow<Technician?>

  /**
   * Adds or updates a technician.
   */
  suspend fun updateTechnician(technician: Technician): Result<Boolean>

  /**
   * Deletes a technician by ID.
   */
  suspend fun deleteTechnician(id: String): Result<Boolean>
}
