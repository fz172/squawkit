package dev.fanfly.wingslog.feature.attachment.datamanager

internal object AttachmentStoragePath {

  fun forMaintenanceLog(
    uid: String,
    aircraftId: String,
    logId: String,
    attachmentId: String,
    filename: String,
  ): String = "users/$uid/fleet/$aircraftId/maintenance_logs/$logId/${attachmentId}_${filename.sanitise()}"

  fun forMaintenanceTask(
    uid: String,
    aircraftId: String,
    cardId: String,
    attachmentId: String,
    filename: String,
  ): String = "users/$uid/fleet/$aircraftId/inspection_cards/$cardId/${attachmentId}_${filename.sanitise()}"

  private fun String.sanitise() = replace(Regex("[^A-Za-z0-9._-]"), "_")
}
