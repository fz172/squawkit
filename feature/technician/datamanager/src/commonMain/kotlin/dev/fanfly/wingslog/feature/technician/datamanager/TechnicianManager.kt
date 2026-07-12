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
   * Firebase account's display name / email and backfilling a blank name. When
   * [replaceExistingName] is true, the account name replaces the current local self name; this is
   * used after guest account upgrade so the guest profile becomes the provider profile.
   *
   * Call this after an account upgrade: linking a provider does not fire `authStateChanged`, so the
   * sign-in bootstrap would otherwise never run and the profile (name + photo) would stay stale.
   */
  suspend fun ensureSelfProfile(replaceExistingName: Boolean = false): Result<Unit>

  /**
   * Applies the user's confirmed reconciliation of look-alike roster rows (design §7.4).
   *
   * A manual↔manual merge tombstones the duplicate rows. A manual↔member merge only *aliases* them
   * (`superseded_by_uid`) — manual rows are user-global while mirrors are per-aircraft, so deleting
   * one because it matched a member on one aircraft would make that person vanish from the picker
   * on another. Warnings are informational and apply nothing.
   *
   * Log snapshots are never touched: a merge changes only the go-forward roster.
   */
  suspend fun applyDuplicateMerges(groups: List<DuplicateGroup>): Result<Unit>

  /** True once the user has dealt with (or dismissed) the duplicate review, so it stops nagging. */
  fun observeDuplicatesReviewed(): Flow<Boolean>

  suspend fun markDuplicatesReviewed(): Result<Unit>
}
