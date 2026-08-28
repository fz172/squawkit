package dev.fanfly.wingslog.feature.login.onboarding

import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val CHOSEN_NAME = "Sponge Bob"

/**
 * The onboarding name step has to reach the Firebase auth profile, not just the local record.
 *
 * Cloud Functions cannot read the self-technician row — they see only the ID token — and
 * `createAircraftShareInvite` stamps an invite's `hostName` from `token.name`. Saving only locally
 * leaves `token.name` empty, so everyone the user invites sees a blank host while the app itself
 * shows the name they chose.
 *
 * This bites hardest with Sign in with Apple, which supplies no display name at all after the first
 * authorization — there is no provider name to fall back on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TechnicianOnboardingActionsTest {

  private val technicianManager: TechnicianManager = mockk(relaxed = true)
  private val authManager: AuthManager = mockk(relaxed = true)
  private val actions =
    TechnicianOnboardingActions(technicianManager, authManager)

  @Test
  fun saveSelfName_persistsLocallyAndMirrorsToTheAuthProfile() = runTest {
    coEvery { technicianManager.saveSelfName(any()) } returns Result.success(
      Unit
    )

    actions.saveSelfName(CHOSEN_NAME)

    coVerify { technicianManager.saveSelfName(CHOSEN_NAME) }
    coVerify { authManager.updateDisplayName(CHOSEN_NAME) }
  }
}
