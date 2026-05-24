package dev.fanfly.wingslog.feature.export.datamanager.impl

/**
 * Per-request disposition strings returned by the requestExportDelivery backend (the
 * `request_outcome` field), distinct from the persisted delivery state in [ExportDeliveryStates].
 * A resend can be throttled while the persisted delivery state still reads SENT, so callers must
 * branch on these to report the real outcome.
 */
internal object ExportRequestOutcomes {
  const val SENT = "sent"
  const val FAILED = "failed"
  const val RESEND_THROTTLED = "resend-throttled"
  const val ALREADY_SENT = "already-sent"
  const val IN_PROGRESS = "in-progress"
}
