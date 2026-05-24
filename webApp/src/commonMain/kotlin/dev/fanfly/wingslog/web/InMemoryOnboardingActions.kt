package dev.fanfly.wingslog.web

import dev.fanfly.wingslog.feature.login.onboarding.OnboardingActions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * TODO(M4): temporary. Replace with the real `TechnicianManager` once `feature:sync:data` and
 * `feature:technician:datamanager` compile on JS. Until then the onboarding name is held in memory
 * only (not persisted across reloads). The `hasSeenWelcome` flag is *not* affected — it persists
 * through `OnboardingPreferences` → the local sql.js store, which is what this milestone exercises.
 */
class InMemoryOnboardingActions : OnboardingActions {
  private val name = MutableStateFlow<String?>(null)

  override fun observeSelfName(): Flow<String?> = name

  override suspend fun saveSelfName(name: String) {
    this.name.value = name
  }
}
