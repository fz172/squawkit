package dev.fanfly.wingslog.feature.sharing.datamanager.impl

import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.sharing.model.AircraftShareState
import dev.fanfly.wingslog.feature.sharing.model.InviteLink
import dev.fanfly.wingslog.feature.sharing.model.RedeemOutcome
import dev.fanfly.wingslog.feature.sharing.model.ShareRole
import kotlinx.coroutines.flow.Flow

/**
 * Scaffold implementation. All operations are unimplemented until the membership plumbing (P2)
 * wires the Firestore/Functions calls. Inert in production: nothing invokes it yet, and all sharing
 * UI is gated behind FeatureFlags.aircraftSharingEnabled.
 */
class SharingManagerImpl : SharingManager {
  override fun observeShareState(acId: String): Flow<AircraftShareState> =
    TODO("SharingManager wiring lands in P2 (#119)")

  override fun observeMyRole(acId: String): Flow<ShareRole?> =
    TODO("SharingManager wiring lands in P2 (#119)")

  override suspend fun createInvite(acId: String, role: ShareRole): Result<InviteLink> =
    TODO("SharingManager wiring lands in P2 (#119)")

  override suspend fun cancelInvite(acId: String, tokenHash: String): Result<Unit> =
    TODO("SharingManager wiring lands in P2 (#119)")

  override suspend fun redeemInvite(acId: String, secret: String): Result<RedeemOutcome> =
    TODO("SharingManager wiring lands in P2 (#119)")

  override suspend fun revokeMember(acId: String, uid: String): Result<Unit> =
    TODO("SharingManager wiring lands in P2 (#119)")

  override suspend fun updateRole(acId: String, uid: String, role: ShareRole): Result<Unit> =
    TODO("SharingManager wiring lands in P2 (#119)")

  override suspend fun leave(acId: String): Result<Unit> =
    TODO("SharingManager wiring lands in P2 (#119)")

  override suspend fun publishTechnicianMirror(): Result<Unit> =
    TODO("SharingManager wiring lands in P2 (#119)")
}
