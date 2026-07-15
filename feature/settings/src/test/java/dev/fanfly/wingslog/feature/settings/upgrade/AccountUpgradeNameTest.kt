package dev.fanfly.wingslog.feature.settings.upgrade

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.auth.AccountUpgradeResult
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.storage.LocalAccountMigrator
import dev.fanfly.wingslog.feature.sync.data.SyncEngine
import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.gitlive.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val UID = "uid-1"
private const val IN_APP_NAME = "Sponge Bob"
private const val GOOGLE_NAME = "Fan Zhang"

/**
 * Upgrading a guest must never rename them.
 *
 * The app used to call `ensureSelfProfile(replaceExistingName = true)`, which overwrote the name the
 * user had typed with whatever Google calls them — so someone who set themselves up as "Sponge Bob"
 * was silently renamed by signing in. The in-app name is the source of truth; the account name only
 * seeds a blank profile.
 *
 * And the reverse direction matters too: Cloud Functions cannot read the self-technician record —
 * they see only the ID token — and `createAircraftShareInvite` stamps an invite's `hostName` from
 * `token.name`. So the in-app name has to be pushed ONTO the auth profile, or the person you invite
 * sees your Google name while the rest of the app shows the name you chose.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountUpgradeNameTest {

  private val dispatcher = UnconfinedTestDispatcher()

  private val authManager: AuthManager = mockk(relaxed = true)
  private val migrator: LocalAccountMigrator = mockk(relaxed = true)
  private val technicianManager: TechnicianManager = mockk(relaxed = true)
  private val syncEngine: SyncEngine = mockk(relaxed = true)

  private val permanentUser: FirebaseUser = mockk {
    every { uid } returns UID
    every { isAnonymous } returns false
    // What Google calls them. It must not end up on the profile, and must not be pushed to the token.
    every { displayName } returns GOOGLE_NAME
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    every { authManager.getCurrentUser() } returns permanentUser
    coEvery { authManager.upgradeAnonymousAccount() } returns
      AccountUpgradeResult.Linked(permanentUser)
    coEvery { technicianManager.ensureSelfProfile() } returns Result.success(Unit)
    every { technicianManager.observeSelf() } returns
      flowOf(Technician(id = "self-1", name = IN_APP_NAME))
  }

  @After
  fun tearDown() = Dispatchers.resetMain()

  private fun viewModel() = AccountUpgradeViewModel(
    authManager = authManager,
    migrator = migrator,
    technicianManager = technicianManager,
    syncEngine = syncEngine,
  )

  @Test
  fun upgrade_pushesTheInAppNameToTheAuthProfile_notTheOtherWayRound() = runTest(dispatcher) {
    viewModel().startUpgrade()
    advanceUntilIdle()

    // The token must carry the name the user chose, because that is what the invite's hostName is
    // stamped from — otherwise an invitee sees a different name from everyone else in the app.
    coVerify { authManager.updateDisplayName(IN_APP_NAME) }
    coVerify(exactly = 0) { authManager.updateDisplayName(GOOGLE_NAME) }
  }

  @Test
  fun upgrade_neverAsksToReplaceTheExistingName() = runTest(dispatcher) {
    // ensureSelfProfile has no replace flag any more; this pins that the upgrade path calls the
    // seed-only version, so a filled-in profile is left alone.
    viewModel().startUpgrade()
    advanceUntilIdle()

    coVerify(exactly = 1) { technicianManager.ensureSelfProfile() }
  }

  @Test
  fun upgrade_withNoInAppNameYet_pushesNothing() = runTest(dispatcher) {
    // A guest who never named themselves has nothing to defend. ensureSelfProfile seeds the blank
    // profile from the account, and there is no in-app name to push back.
    every { technicianManager.observeSelf() } returns flowOf(Technician(id = "self-1", name = ""))

    viewModel().startUpgrade()
    advanceUntilIdle()

    coVerify(exactly = 0) { authManager.updateDisplayName(any()) }
  }

  @Test
  fun merge_carriesTheWelcomeScreenNameOntoTheExistingAccount() = runTest(dispatcher) {
    // Collision path: the chosen provider already owns an account, so we sign IN to it and re-key the
    // guest's data across. The name the user typed on the welcome screen must follow them, even
    // though the account keeps its own identity — otherwise merging silently renames the user to
    // whatever the existing account was called.
    val guest: FirebaseUser = mockk {
      every { uid } returns "guest-uid"
      every { isAnonymous } returns true
    }
    // First call captures the guest UID; the rest satisfy awaitPermanentCurrentUser (now the account).
    val currentUsers = ArrayDeque(listOf(guest, permanentUser))
    every { authManager.getCurrentUser() } answers { currentUsers.removeFirstOrNull() ?: permanentUser }
    coEvery { authManager.upgradeAnonymousAccount() } returns
      AccountUpgradeResult.CredentialInUse(mockk())
    coEvery { authManager.signInToExistingAccount(any()) } returns
      AccountUpgradeResult.Linked(permanentUser)
    coEvery { technicianManager.saveSelfName(any()) } returns Result.success(Unit)

    viewModel().startUpgrade()
    advanceUntilIdle()

    coVerify { migrator.reassign(fromUid = "guest-uid", toUid = UID) }
    // The welcome-screen name is stamped onto the account's self-technician and pushed to the token.
    coVerify { technicianManager.saveSelfName(IN_APP_NAME) }
    coVerify { authManager.updateDisplayName(IN_APP_NAME) }
  }

  @Test
  fun merge_withNoWelcomeScreenName_leavesTheAccountNameAlone() = runTest(dispatcher) {
    // A guest who never named themselves brings no name to carry, so the account keeps its own.
    every { technicianManager.observeSelf() } returns flowOf(Technician(id = "self-1", name = ""))
    val guest: FirebaseUser = mockk {
      every { uid } returns "guest-uid"
      every { isAnonymous } returns true
    }
    val currentUsers = ArrayDeque(listOf(guest, permanentUser))
    every { authManager.getCurrentUser() } answers { currentUsers.removeFirstOrNull() ?: permanentUser }
    coEvery { authManager.upgradeAnonymousAccount() } returns
      AccountUpgradeResult.CredentialInUse(mockk())
    coEvery { authManager.signInToExistingAccount(any()) } returns
      AccountUpgradeResult.Linked(permanentUser)

    viewModel().startUpgrade()
    advanceUntilIdle()

    coVerify(exactly = 0) { technicianManager.saveSelfName(any()) }
  }
}
