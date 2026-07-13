package dev.fanfly.wingslog.feature.technician.datamanager

import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.feature.technician.datamanager.merge.DuplicateGroup
import kotlinx.coroutines.flow.Flow

interface TechnicianManager {
  fun observeTechnicians(): Flow<List<Technician>>

  fun loadTechnician(id: String): Flow<Technician?>

  /** Resolves the current user's technician record via UserInfo.self_technician_id. */
  fun observeSelf(): Flow<Technician?>

  /** The raw self-technician ID — exposed separately for list-row badging without double-loading. */
  fun observeSelfId(): Flow<String?>

  suspend fun updateTechnician(technician: Technician): Result<Boolean>

  suspend fun deleteTechnician(id: String): Result<Boolean>

  /**
   * Creates or updates the current user's self-technician record with [name].
   * Used during onboarding when the user enters their display name.
   * Handles anonymous users who have no bootstrapped technician record yet.
   */
  suspend fun saveSelfName(name: String): Result<Unit>

  /**
   * Ensures the signed-in (non-anonymous) user has a self-technician, seeding its name from the
   * Firebase account (display name / email) **only when there is no name yet**.
   *
   * The in-app name wins. It used to be the other way round: upgrading a guest replaced the name the
   * user had typed with whatever Google called them — so someone who set themselves up as "Sponge
   * Bob" was silently renamed on sign-in. The account name is a seed for an empty profile, not an
   * authority over one the user has already filled in.
   *
   * Call this after an account upgrade: linking a provider does not fire `authStateChanged`, so the
   * sign-in bootstrap would otherwise never run and a blank profile would stay blank.
   */
  suspend fun ensureSelfProfile(): Result<Unit>

  /**
   * Applies the user's confirmed reconciliation of look-alike roster rows (design §7.4).
   *
   * A merge deletes the duplicate rows outright, whether the keeper is another manual row or a share
   * member's mirror. Warnings are informational and apply nothing.
   *
   * Log snapshots are never touched: a merge changes only the go-forward roster, so already-signed
   * work keeps whatever technician it recorded.
   */
  suspend fun applyDuplicateMerges(
    groups: List<DuplicateGroup>,
    reviewedSignature: String,
  ): Result<Unit>

  /**
   * Signature of the duplicate set the user last reviewed, or null if they never have.
   *
   * Deliberately not a boolean: "reviewed" must mean *these* duplicates, not "never nag me again".
   * A boolean here permanently mutes the prompt, so every look-alike added afterwards is detected
   * and then silently swallowed.
   */
  fun observeReviewedDuplicatesSignature(): Flow<String?>

  suspend fun markDuplicatesReviewed(signature: String): Result<Unit>
}
