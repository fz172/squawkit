package dev.fanfly.wingslog.feature.inspection.datamanager

import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.feature.inspection.model.DueMetadata
import kotlinx.coroutines.flow.Flow


interface InspectionManager {

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

  /**
   * Compute the next-due status for a given inspection card, given the aircraft's maintenance logs.
   *
   * Logic:
   * - If the card has a force_due_date or force_due_tach set, return those directly.
   * - Otherwise, find the most recent log that references this card ID, then add the rule interval.
   * - If no log found, due status uses the rule interval from "now".
   */
  suspend fun computeNextDue(
    card: InspectionCard,
    logs: List<MaintenanceLog>,
    allCards: List<InspectionCard> = emptyList(),
  ): DueMetadata
}
