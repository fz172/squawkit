package dev.fanfly.wingslog.feature.login.onboarding

import kotlinx.coroutines.flow.Flow

/**
 * App-provided side effects the onboarding flow needs beyond Firebase auth: persisting and
 * observing the signed-in user's display name.
 *
 * composeApp backs this with the real `TechnicianManager` (EntityStore<Technician>). webApp uses
 * a temporary in-memory implementation until the sync stack (and thus TechnicianManager) lands on
 * JS in M4 — see the web wiring's TODO.
 */
interface OnboardingActions {
  fun observeSelfName(): Flow<String?>
  suspend fun saveSelfName(name: String)
}
