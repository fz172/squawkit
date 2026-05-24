package dev.fanfly.wingslog.feature.export.datamanager

/**
 * Result of a manual delivery request (resend or retry) for an already-uploaded export, surfaced to
 * the UI so it can report the outcome to the user.
 */
sealed interface ExportDeliveryOutcome {
  /** A fresh delivery email was actually sent. */
  data object Sent : ExportDeliveryOutcome

  /** The export was already sent recently and the resend was throttled — no new email went out. */
  data object Throttled : ExportDeliveryOutcome

  /** A delivery is already in flight, so the request was a no-op. */
  data object InProgress : ExportDeliveryOutcome

  /** Delivery did not succeed. [reason] carries the server-provided detail when available. */
  data class Failed(val reason: String = "") : ExportDeliveryOutcome
}
