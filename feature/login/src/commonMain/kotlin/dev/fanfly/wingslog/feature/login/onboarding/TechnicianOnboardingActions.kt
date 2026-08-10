package dev.fanfly.wingslog.feature.login.onboarding

import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Persists onboarding display names through the local-first technician manager. */
class TechnicianOnboardingActions(
  private val technicianManager: TechnicianManager,
  private val authManager: AuthManager,
) : OnboardingActions {

  override fun observeSelfName(): Flow<String?> =
    technicianManager.observeSelf()
      .map { it?.name }

  /**
   * Writes the name to the local technician record *and* mirrors it to the Firebase auth profile.
   *
   * The local record is the source of truth in-app, but Cloud Functions cannot read it — they see
   * only the ID token. `createAircraftShareInvite` stamps an invite's `hostName` from `token.name`,
   * so without the mirror an invitee sees a blank or provider-supplied host instead of the name the
   * user actually chose. This matters most for Sign in with Apple, which supplies no display name
   * at all after the first authorization, but it is the same reasoning `AccountUpgradeViewModel`
   * already applies on the upgrade path. Best-effort — [AuthManager.updateDisplayName] swallows its
   * own failures.
   */
  override suspend fun saveSelfName(name: String) {
    technicianManager.saveSelfName(name)
    authManager.updateDisplayName(name)
  }
}
