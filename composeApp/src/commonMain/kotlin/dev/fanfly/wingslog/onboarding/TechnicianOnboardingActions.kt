package dev.fanfly.wingslog.onboarding

import dev.fanfly.wingslog.feature.login.onboarding.OnboardingActions
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Mobile [OnboardingActions]: the display name is the current user's self-technician, persisted
 * and observed through the real [TechnicianManager] (EntityStore<Technician>).
 */
class TechnicianOnboardingActions(
  private val technicianManager: TechnicianManager,
) : OnboardingActions {

  override fun observeSelfName(): Flow<String?> =
    technicianManager.observeSelf().map { it?.name }

  override suspend fun saveSelfName(name: String) {
    technicianManager.saveSelfName(name)
  }
}
