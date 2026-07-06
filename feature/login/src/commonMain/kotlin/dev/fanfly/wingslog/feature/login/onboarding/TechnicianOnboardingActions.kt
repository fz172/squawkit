package dev.fanfly.wingslog.feature.login.onboarding

import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Persists onboarding display names through the local-first technician manager. */
class TechnicianOnboardingActions(
  private val technicianManager: TechnicianManager,
) : OnboardingActions {

  override fun observeSelfName(): Flow<String?> =
    technicianManager.observeSelf()
      .map { it?.name }

  override suspend fun saveSelfName(name: String) {
    technicianManager.saveSelfName(name)
  }
}
