package dev.fanfly.wingslog.feature.login.onboarding

import kotlinx.coroutines.flow.Flow

/**
 * Side effects the onboarding flow needs beyond Firebase auth: persisting and observing the
 * signed-in user's display name. [TechnicianOnboardingActions] backs the production flow with the
 * real local-first technician manager.
 */
interface OnboardingActions {
  fun observeSelfName(): Flow<String?>
  suspend fun saveSelfName(name: String)
}
