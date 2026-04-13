package dev.fanfly.wingslog.feature.inspection.datamanager

import dev.fanfly.wingslog.aircraft.InspectionCard
import kotlinx.coroutines.flow.Flow

interface InspectionDataManager {

  /**
   * Observe all inspection cards for an aircraft in real-time.
   */
  fun observeInspections(aircraftId: String): Flow<List<InspectionCard>>

  /**
   * Add a new inspection card to an aircraft.
   */
  suspend fun addInspection(aircraftId: String, card: InspectionCard): Result<Boolean>

  /**
   * Update an existing inspection card.
   */
  suspend fun updateInspection(aircraftId: String, card: InspectionCard): Result<Boolean>

  /**
   * Delete an inspection card. Logs that reference the card's ID will have orphaned IDs,
   * which are silently ignored during display.
   */
  suspend fun deleteInspection(aircraftId: String, cardId: String): Result<Boolean>
}
