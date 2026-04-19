package dev.fanfly.wingslog.feature.tasks.datamanager

import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata

interface TaskDueManager {

  /**
   * Compute the next-due status for a given inspection card, given the aircraft's maintenance logs.
   *
   * Logic:
   * - If the card has a force_due_date or force_due_engine_hour set, return those directly.
   * - Otherwise, find the most recent log that references this card ID, then add the rule interval.
   * - If no log found, due status uses the rule interval from "now".
   * - Linked rules recursively resolve against their parent card's metadata, with cycle protection.
   */
  fun computeNextDue(
    card: MaintenanceTask,
    logs: List<MaintenanceLog>,
    allCards: List<MaintenanceTask> = emptyList(),
  ): DueMetadata
}
