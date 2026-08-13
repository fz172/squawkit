package dev.fanfly.wingslog.core.auth

/**
 * Permanently deletes the signed-in account and everything belonging to it (#418).
 *
 * Required by App Store Review Guideline 5.1.1(v). An interface so the destructive call can be
 * faked in tests — nothing here is recoverable, so "it was only a mock" matters.
 */
interface AccountDeleter {
  /**
   * Deletes the account server-side. Returns false if it did not happen, in which case the caller
   * must leave the local data alone: wiping the device after a failed delete would destroy the only
   * remaining copy of records the account still holds.
   */
  suspend fun deleteAccount(): Boolean
}
