package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.fanfly.wingslog.export.ExportRecord

internal object ExportRecordMerge {
  fun merge(
    local: List<ExportRecord>,
    remote: List<ExportRecord>
  ): List<ExportRecord> {
    val localById = local.associateBy { it.export_id }
    val remoteById = remote.associateBy { it.export_id }
    return (localById.keys + remoteById.keys).mapNotNull { exportId ->
      val localRecord = localById[exportId]
      val remoteRecord = remoteById[exportId]
      when {
        localRecord != null && remoteRecord != null -> localRecord.withRemote(
          remoteRecord
        )

        localRecord != null -> localRecord
        remoteRecord != null -> remoteRecord
        else -> null
      }
    }
      .sortedByDescending { it.created_at_epoch_millis }
  }

  private fun ExportRecord.withRemote(remote: ExportRecord) = copy(
    remote_archive_ref = remote.remote_archive_ref.ifBlank { remote_archive_ref },
    destination_email = remote.destination_email.ifBlank { destination_email },
    destination_email_source = remote.destination_email_source.ifBlank { destination_email_source },
    persisted_delivery_state = remote.persisted_delivery_state.ifBlank { persisted_delivery_state },
    delivery_sent_at_epoch_millis =
      remote.delivery_sent_at_epoch_millis.takeIf { it > 0L }
        ?: delivery_sent_at_epoch_millis,
    delivery_failure_code = remote.delivery_failure_code.ifBlank { delivery_failure_code },
    delivery_failure_message = remote.delivery_failure_message.ifBlank { delivery_failure_message },
    remote_expires_at_epoch_millis =
      remote.remote_expires_at_epoch_millis.takeIf { it > 0L }
        ?: remote_expires_at_epoch_millis,
  )
}
