package dev.fanfly.wingslog.feature.notifications.model

/**
 * One notification channel per class (design §5.2, Q8), so OS-level controls (Android's
 * per-channel settings, iOS's per-category) mirror the in-app preference groups in §8.3 rather than
 * lumping everything under one importance level.
 */
enum class NotificationChannel {
  /** N1 — someone else changed a shared thing. */
  COLLABORATION,

  /**
   * N2 — a squawk or inspection crossed into a more urgent tier, including a squawk reaching AOG.
   * AOG is not its own channel (design decision, 2026-08-26) — it reports exactly like any other
   * priority escalation.
   */
  URGENCY_UPDATE,
}
