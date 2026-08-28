package dev.fanfly.wingslog.feature.notifications.datamanager

import dev.fanfly.wingslog.core.auth.AuthManager
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignOutCoordinatorTest {

  private val authManager = mockk<AuthManager>(relaxed = true)
  private val registrar = mockk<PushTokenRegistrar>(relaxed = true)

  /**
   * The order is the property. Rules gate the `push_devices` delete on `request.auth.uid`, so a
   * clear attempted after the sign-out can only be permission-denied — the token would survive.
   */
  @Test
  fun `clears this device before signing out`() = runTest {
    coJustRun { registrar.clearThisDevice() }

    coordinator().signOut()

    coVerifyOrder {
      registrar.clearThisDevice()
      authManager.logOut()
    }
  }

  /**
   * The #550 case. Offline, a Firestore write never settles rather than failing, so an unbounded
   * wait would make signing out a dead action in airplane mode — no sign-out, no error, nothing.
   */
  @Test
  fun `signs out anyway when the clear never settles`() = runTest {
    coEvery { registrar.clearThisDevice() } coAnswers { awaitCancellation() }

    coordinator().signOut()
    advanceUntilIdle()

    coVerify { authManager.logOut() }
  }

  /** iOS until P5, web by design: no registrar bound, and signing out must still work. */
  @Test
  fun `signs out on a platform with no push transport`() = runTest {
    SignOutCoordinator(authManager, pushTokenRegistrar = null).signOut()

    coVerify { authManager.logOut() }
  }

  /**
   * A failing clear is not a reason to strand someone in an account they asked to leave — and the
   * timeout alone does not cover this: `withTimeoutOrNull` swallows a hang, not a throw.
   */
  @Test
  fun `signs out even when the clear throws`() = runTest {
    coEvery { registrar.clearThisDevice() } throws IllegalStateException("permission denied")

    coordinator().signOut()

    coVerify { authManager.logOut() }
  }

  private fun coordinator() = SignOutCoordinator(authManager, registrar)
}
